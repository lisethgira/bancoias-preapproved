import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UsageRequestPayload, UsageRequestResponse } from '../models/usage-request.model';

@Injectable({ providedIn: 'root' })
export class UsageRequestService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/usage-requests';

  process(payload: UsageRequestPayload): Observable<UsageRequestResponse> {
    return this.http.post<UsageRequestResponse>(this.baseUrl, payload);
  }

  findByReference(reference: string): Observable<UsageRequestResponse> {
    return this.http.get<UsageRequestResponse>(`${this.baseUrl}/${encodeURIComponent(reference)}`);
  }

  findRecent(limit = 20): Observable<UsageRequestResponse[]> {
    return this.http.get<UsageRequestResponse[]>(this.baseUrl, { params: { limit } });
  }
}