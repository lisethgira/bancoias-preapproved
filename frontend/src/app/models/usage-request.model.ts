export type UsageRequestStatus = 'AUTHORIZED' | 'REJECTED';

export interface UsageRequestPayload {
  requestReference: string;
  preApprovedId: string;
  customerId: string;
  amount: number;
}

export interface UsageRequestResponse {
  requestReference: string;
  preApprovedId: string;
  customerId: string;
  amount: number;
  status: UsageRequestStatus;
  rejectionReason: string | null;
  rejectionMessage: string | null;
  processedAt: string;
  replayed: boolean;
}

export interface ApiError {
  code: string;
  message: string;
  details: string[];
  originalRequest: UsageRequestResponse | null;
  timestamp: string;
}