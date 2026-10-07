import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { UsageRequestResponse } from '../../models/usage-request.model';

@Component({
  selector: 'app-recent-requests',
  imports: [CurrencyPipe, DatePipe],
  template: `
    <section class="card">
      <div class="header">
        <h2>Solicitudes procesadas recientemente</h2>
        <button type="button" class="secondary" (click)="refresh.emit()" [disabled]="loading()">
          {{ loading() ? 'Cargando...' : 'Actualizar' }}
        </button>
      </div>

      @if (requests().length === 0) {
        <p class="empty">Aún no hay solicitudes procesadas.</p>
      } @else {
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Referencia</th>
                <th>Preaprobado</th>
                <th>Cliente</th>
                <th class="num">Valor</th>
                <th>Estado</th>
                <th>Razón</th>
                <th>Procesada</th>
              </tr>
            </thead>
            <tbody>
              @for (r of requests(); track r.requestReference) {
                <tr>
                  <td>{{ r.requestReference }}</td>
                  <td>{{ r.preApprovedId }}</td>
                  <td>{{ r.customerId }}</td>
                  <td class="num">{{ r.amount | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                  <td>
                    <span class="status" [class.ok]="r.status === 'AUTHORIZED'" [class.ko]="r.status === 'REJECTED'">
                      {{ r.status === 'AUTHORIZED' ? 'Autorizada' : 'Rechazada' }}
                    </span>
                  </td>
                  <td class="muted">{{ r.rejectionMessage ?? '—' }}</td>
                  <td class="muted">{{ r.processedAt | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    .header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .header h2 { margin: 0; }
    .table-wrapper { overflow-x: auto; margin-top: 1rem; }
    table { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
    th, td { text-align: left; padding: 0.55rem 0.6rem; border-bottom: 1px solid var(--border); white-space: nowrap; }
    th { color: var(--muted); font-weight: 600; font-size: 0.8rem; }
    .num { text-align: right; }
    .muted { color: var(--muted); }
    .status { font-weight: 700; }
    .status.ok { color: var(--success); }
    .status.ko { color: var(--danger); }
  `,
})
export class RecentRequests {
  readonly requests = input<UsageRequestResponse[]>([]);
  readonly loading = input(false);
  readonly refresh = output<void>();
}