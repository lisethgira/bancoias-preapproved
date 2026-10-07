import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { RecentRequests } from './components/recent-requests/recent-requests';
import { RequestResult } from './components/request-result/request-result';
import { UsageRequestForm } from './components/usage-request-form/usage-request-form';
import { ApiError, UsageRequestPayload, UsageRequestResponse } from './models/usage-request.model';
import { UsageRequestService } from './services/usage-request.service';

@Component({
  selector: 'app-root',
  imports: [UsageRequestForm, RequestResult, RecentRequests],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly service = inject(UsageRequestService);

  readonly submitting = signal(false);
  readonly lastResult = signal<UsageRequestResponse | null>(null);
  readonly apiError = signal<ApiError | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly recent = signal<UsageRequestResponse[]>([]);
  readonly loadingRecent = signal(false);

  ngOnInit(): void {
    this.loadRecent();
  }

  onSubmit(payload: UsageRequestPayload): void {
    this.submitting.set(true);
    this.lastResult.set(null);
    this.apiError.set(null);
    this.errorMessage.set(null);

    this.service
      .process(payload)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (result) => {
          this.lastResult.set(result);
          this.loadRecent();
        },
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  loadRecent(): void {
    this.loadingRecent.set(true);
    this.service
      .findRecent(20)
      .pipe(finalize(() => this.loadingRecent.set(false)))
      .subscribe({
        next: (requests) => this.recent.set(requests),
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  private handleError(error: HttpErrorResponse): void {
    if (error.status === 0) {
      this.errorMessage.set('No se pudo conectar con el backend. Verifica que esté en ejecución.');
    } else if (error.error?.code) {
      this.apiError.set(error.error as ApiError);
    } else {
      this.errorMessage.set(`Error inesperado (HTTP ${error.status}).`);
    }
  }
}