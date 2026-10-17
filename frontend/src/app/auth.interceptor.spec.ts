import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });

  it('fetches a CSRF token and sends it in the header for API write requests', async () => {
    let completed = false;
    http.post('/api/memberships', {}).subscribe(() => completed = true);

    const csrfRequest = httpTesting.expectOne('/api/csrf');
    expect(csrfRequest.request.method).toBe('GET');
    expect(csrfRequest.request.withCredentials).toBe(true);
    csrfRequest.flush({ token: 'fresh-csrf-token' });
    await new Promise((resolve) => setTimeout(resolve, 0));
    const request = httpTesting.expectOne('/api/memberships');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('fresh-csrf-token');
    request.flush({});
    expect(completed).toBe(true);
  });

  it('fetches a fresh token on a later write and retries once after a CSRF rejection', async () => {
    http.post('/api/memberships', {}).subscribe();

    const firstTokenRequest = httpTesting.expectOne('/api/csrf');
    firstTokenRequest.flush({ token: 'first-token' });
    await new Promise((resolve) => setTimeout(resolve, 0));
    const firstWrite = httpTesting.expectOne('/api/memberships');
    expect(firstWrite.request.headers.get('X-XSRF-TOKEN')).toBe('first-token');
    firstWrite.flush({ message: 'The security token is missing or expired.' }, {
      status: 403,
      statusText: 'Forbidden',
    });

    const retryTokenRequest = httpTesting.expectOne('/api/csrf');
    retryTokenRequest.flush({ token: 'fresh-token' });
    await new Promise((resolve) => setTimeout(resolve, 0));
    const retry = httpTesting.expectOne('/api/memberships');
    expect(retry.request.headers.get('X-XSRF-TOKEN')).toBe('fresh-token');
    retry.flush({});

    http.post('/api/memberships', {}).subscribe();
    const nextTokenRequest = httpTesting.expectOne('/api/csrf');
    nextTokenRequest.flush({ token: 'next-token' });
    await new Promise((resolve) => setTimeout(resolve, 0));
    const nextWrite = httpTesting.expectOne('/api/memberships');
    expect(nextWrite.request.headers.get('X-XSRF-TOKEN')).toBe('next-token');
    nextWrite.flush({});
  });

  it('does not add a CSRF header to read requests', () => {
    http.get('/api/memberships').subscribe();

    const request = httpTesting.expectOne('/api/memberships');
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    request.flush([]);
  });

  it('does not request a CSRF token for public auth writes', () => {
    http.post('/api/auth/login', {}).subscribe();

    const request = httpTesting.expectOne('/api/auth/login');
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    request.flush({});
  });
});
