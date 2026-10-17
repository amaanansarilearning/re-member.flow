import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { CsrfTokenService } from './csrf-token.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const unsafeMethod = !['GET', 'HEAD', 'OPTIONS'].includes(request.method);
  const isProtectedApiWrite = unsafeMethod
    && request.url.startsWith('/api/')
    && !request.url.startsWith('/api/auth/');
  const authenticatedRequest = request.clone({ withCredentials: true });
  const csrfTokens = inject(CsrfTokenService);
  const sendProtectedRequest = () => from(csrfTokens.getToken()).pipe(
    switchMap((token) => next(authenticatedRequest.clone({
      setHeaders: { 'X-XSRF-TOKEN': token }
    })))
  );
  const securedRequest$ = isProtectedApiWrite
    ? sendProtectedRequest().pipe(
      catchError((error: unknown) => {
        if (isCsrfRejection(error)) {
          csrfTokens.clear();
          return sendProtectedRequest();
        }
        return throwError(() => error);
      })
    )
    : next(authenticatedRequest);

  return securedRequest$.pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && typeof sessionStorage !== 'undefined') {
        const isPublicAuthRequest = ['/auth/login', '/auth/setup-owner', '/auth/password-reset/request',
          '/auth/password-reset/confirm'].some((path) => request.url.endsWith(path));
        
        if (!isPublicAuthRequest) {
          sessionStorage.removeItem('gym.user');
          sessionStorage.setItem('gym.session-expired', '1');
          if (typeof window !== 'undefined') window.location.reload();
        }
      }
      return throwError(() => error);
    })
  );
};

function isCsrfRejection(error: unknown): error is HttpErrorResponse {
  return error instanceof HttpErrorResponse
    && error.status === 403
    && typeof error.error?.message === 'string'
    && error.error.message.toLowerCase().includes('security token');
}