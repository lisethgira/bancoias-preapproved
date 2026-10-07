import { Component, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UsageRequestPayload } from '../../models/usage-request.model';

@Component({
  selector: 'app-usage-request-form',
  imports: [ReactiveFormsModule],
  template: `
    <form class="card" [formGroup]="form" (ngSubmit)="submit()">
      <h2>Nueva solicitud</h2>

      <label for="reference">Referencia</label>
      <div class="row">
        <input id="reference" formControlName="requestReference" />
        <button type="button" class="secondary" (click)="newReference()">Nueva referencia</button>
      </div>
      @if (form.controls.requestReference.invalid && form.controls.requestReference.touched) {
        <small class="field-error">La referencia es obligatoria (máximo 50 caracteres).</small>
      }

      <label for="preApproved">Preaprobado</label>
      <select id="preApproved" formControlName="preApprovedId">
        @for (option of preApprovedOptions; track option.id) {
          <option [value]="option.id">{{ option.label }}</option>
        }
      </select>

      <label for="customer">Cliente</label>
      <select id="customer" formControlName="customerId">
        @for (customer of customers; track customer) {
          <option [value]="customer">{{ customer }}</option>
        }
      </select>

      <label for="amount">Valor solicitado (COP)</label>
      <input id="amount" type="number" formControlName="amount" />
      @if (form.controls.amount.invalid && form.controls.amount.touched) {
        <small class="field-error">El valor es obligatorio.</small>
      }

      <div class="actions">
        <button type="submit" [disabled]="submitting()">
          {{ submitting() ? 'Procesando...' : 'Enviar solicitud' }}
        </button>
      </div>
      <p class="hint">
        Para verificar la idempotencia, envía de nuevo sin cambiar la referencia.
        Para un conflicto, cambia el valor y conserva la referencia.
      </p>
    </form>
  `,
  styles: `
    .actions { margin-top: 1.25rem; }
    .actions button { width: 100%; }
  `,
})
export class UsageRequestForm {
  readonly submitting = input(false);
  readonly submitted = output<UsageRequestPayload>();

  private readonly fb = inject(NonNullableFormBuilder);

  readonly preApprovedOptions = [
    { id: 'PRA-1001', label: 'PRA-1001 · USR-10 · Activo' },
    { id: 'PRA-1002', label: 'PRA-1002 · USR-10 · Bloqueado' },
    { id: 'PRA-2001', label: 'PRA-2001 · USR-20 · Activo' },
    { id: 'PRA-9999', label: 'PRA-9999 · No existe (prueba)' },
  ];

  readonly customers = ['USR-10', 'USR-20'];

  // El valor no se valida como mayor que cero aquí a propósito:
  // esa regla de negocio la aplica el backend y la solicitud queda registrada como rechazada.
  readonly form = this.fb.group({
    requestReference: [this.generateReference(), [Validators.required, Validators.maxLength(50)]],
    preApprovedId: ['PRA-1001', Validators.required],
    customerId: ['USR-10', Validators.required],
    amount: [600000, Validators.required],
  });

  newReference(): void {
    this.form.controls.requestReference.setValue(this.generateReference());
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submitted.emit({ ...value, requestReference: value.requestReference.trim() });
  }

  private generateReference(): string {
    return 'REF-' + crypto.randomUUID().slice(0, 8).toUpperCase();
  }
}