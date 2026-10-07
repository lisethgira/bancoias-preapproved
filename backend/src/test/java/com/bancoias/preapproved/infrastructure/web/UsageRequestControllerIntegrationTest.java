package com.bancoias.preapproved.infrastructure.web;

import com.bancoias.preapproved.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UsageRequestControllerIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DatabaseClient databaseClient;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).build();
        databaseClient.sql("DELETE FROM usage_request").then().block();
        databaseClient.sql("UPDATE pre_approved SET available_amount = 1000000 WHERE id = 'PRA-1001'").then().block();
    }

    private WebTestClient.ResponseSpec post(String json) {
        return client.post().uri("/api/usage-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange();
    }

    private String body(String reference, String amount) {
        return """
                {"requestReference":"%s","preApprovedId":"PRA-1001","customerId":"USR-10","amount":%s}
                """.formatted(reference, amount);
    }

    @Test
    @DisplayName("POST nueva solicitud válida responde 201 AUTHORIZED")
    void newRequestReturnsCreated() {
        post(body("API-001", "600000"))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("AUTHORIZED")
                .jsonPath("$.replayed").isEqualTo(false)
                .jsonPath("$.processedAt").exists();
    }

    @Test
    @DisplayName("POST referencia repetida responde 200 con replayed=true")
    void repeatedReferenceReturnsOk() {
        post(body("API-002", "600000")).expectStatus().isCreated();

        post(body("API-002", "600000"))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("AUTHORIZED")
                .jsonPath("$.replayed").isEqualTo(true);
    }

    @Test
    @DisplayName("POST misma referencia con otro monto responde 409 con la solicitud original")
    void conflictingReferenceReturnsConflict() {
        post(body("API-003", "600000")).expectStatus().isCreated();

        post(body("API-003", "100"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("REQUEST_REFERENCE_CONFLICT")
                .jsonPath("$.originalRequest.amount").isEqualTo(600000.00);
    }

    @Test
    @DisplayName("POST con monto cero se procesa y queda REJECTED por INVALID_AMOUNT")
    void zeroAmountIsProcessedAsRejected() {
        post(body("API-004", "0"))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.rejectionReason").isEqualTo("INVALID_AMOUNT")
                .jsonPath("$.rejectionMessage").exists();
    }

    @Test
    @DisplayName("POST con campos faltantes responde 400")
    void missingFieldsReturnBadRequest() {
        post("""
                {"requestReference":"","preApprovedId":"PRA-1001","customerId":"USR-10"}
                """)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.details").isNotEmpty();
    }

    @Test
    @DisplayName("GET por referencia responde 200 si existe y 404 si no")
    void findByReference() {
        post(body("API-005", "100000")).expectStatus().isCreated();

        client.get().uri("/api/usage-requests/API-005").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.requestReference").isEqualTo("API-005");

        client.get().uri("/api/usage-requests/NO-EXISTE").exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET recientes devuelve la más reciente primero")
    void findRecent() {
        post(body("API-006", "100000")).expectStatus().isCreated();
        post(body("API-007", "100000")).expectStatus().isCreated();

        client.get().uri("/api/usage-requests?limit=10").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].requestReference").isEqualTo("API-007");
    }
}