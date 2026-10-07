package com.bancoias.preapproved;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PreapprovedApplicationTests {

    @Test
    void contextLoads() {
    }
}