import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { UsageRequestResponse } from '../../models/usage-request.model';

@Component({
  selector: 'app-request-result',
  imports: [CurrencyPipe, DatePipe],
  template: `
    @if (result(); as r) {
      <div class="result" [class.authorized]="r.status === 'AUTHORIZED'" [class.rejected]="r.status === 'REJECTED'">
        <div class="badges">
          <span class="badge">{{ r.status === 'AUTHORIZED' ? 'Autorizada' : 'Rechazada' }}</span>
          @if (r.replayed) {
            <span class="badge replayed">Reintento · se devolvió el resultado original</span>
          }
        </div>

        @if (r.rejectionMessage) {
          <p class="reason">{{ r.rejectionMessage }} <code>{{ r.rejectionReason }}</code></p>
        }

        <dl>
          <dt>Referencia</dt><dd>{{ r.requestReference }}</dd>
          <dt>Preaprobado</dt><dd>{{ r.preApprovedId }}</dd>
          <dt>Cliente</dt><dd>{{ r.customerId }}</dd>
          <dt>Valor</dt><dd>{{ r.amount | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</dd>
          <dt>Procesada</dt><dd>{{ r.processedAt | date: 'dd/MM/yyyy HH:mm:ss' }}</dd>
        </dl>
      </div>
    }
  `,
  styles: `
    .result {
      border-radius: 8px;
      padding: 1rem;
      border-left: 5px solid var(--border);
    }
    .result.authorized { background: var(--success-bg); border-left-color: var(--success); }
    .result.rejected { background: var(--danger-bg); border-left-color: var(--danger); }

    .badges { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 0.75rem; }
    .badge {
      font-weight: 700;
      font-size: 0.85rem;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      background: var(--surface);
    }
    .authorized .badge:first-child { color: var(--success); }
    .rejected .badge:first-child { color: var(--danger); }
    .badge.replayed { background: var(--info-bg); color: var(--primary); }

    .reason { margin: 0 0 0.75rem; font-weight: 600; }
    code { font-size: 0.8rem; opacity: 0.8; }

    dl {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 0.35rem 1rem;
      margin: 0;
      font-size: 0.9rem;
    }
    dt { color: var(--muted); }
    dd { margin: 0; font-weight: 600; }
  `,
})
export class RequestResult {
  readonly result = input<UsageRequestResponse | null>(null);
}