import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

interface CsrfTokenResponse {
  token: string;
}

@Injectable({ providedIn: 'root' })
export class CsrfTokenService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private pending: Promise<string> | null = null;

  getToken(): Promise<string> {
    if (this.pending) return this.pending;

    this.pending = firstValueFrom(
      this.http.get<CsrfTokenResponse>('/api/csrf', { withCredentials: true })
    ).then(({ token }) => {
      if (!token) throw new Error('The API returned an empty CSRF token.');
      return token;
    }).finally(() => {
      this.pending = null;
    });

    return this.pending;
  }

  clear(): void {
    this.pending = null;
  }
}
