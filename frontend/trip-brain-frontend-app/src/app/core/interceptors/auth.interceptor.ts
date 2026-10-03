import {
  HttpInterceptorFn,
  HttpRequest,
  HttpHandlerFn,
  HttpEvent,
  HttpErrorResponse,
  HttpResponse,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { Observable, throwError, BehaviorSubject, of } from 'rxjs';
import { catchError, switchMap, filter, take, timeout } from 'rxjs/operators';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (typeof window === 'undefined') {
    // Return empty list by default for GET list endpoints during SSR, otherwise empty object
    const isGet = req.method === 'GET';
    const mockBody = isGet ? [] : {};
    return of(new HttpResponse({ status: 200, body: mockBody }));
  }

  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  let authReq = req;
  if (token && req.url.includes('/api')) {
    authReq = addTokenHeader(req, token);
  }

  return next(authReq).pipe(
    catchError((error) => {
      const isAuthEndpoint =
        req.url.includes('/auth/login') ||
        req.url.includes('/auth/refresh') ||
        req.url.includes('/auth/register');

      // Intercept 401 Unauthorized or 403 Forbidden / 4XX auth failures
      if (
        error instanceof HttpErrorResponse &&
        (error.status === 401 || error.status === 403) &&
        !isAuthEndpoint &&
        authService.getRefreshToken()
      ) {
        return handleAuthError(authReq, next, authService);
      }
      return throwError(() => error);
    }),
  );
};

function addTokenHeader(request: HttpRequest<any>, token: string): HttpRequest<any> {
  return request.clone({
    headers: request.headers.set('Authorization', 'Bearer ' + token),
  });
}

function handleAuthError(
  request: HttpRequest<any>,
  next: HttpHandlerFn,
  authService: AuthService,
): Observable<HttpEvent<any>> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshTokenSubject.next(null);

    const refreshToken = authService.getRefreshToken();
    if (!refreshToken) {
      isRefreshing = false;
      authService.logout();
      return throwError(() => new Error('No refresh token available'));
    }

    return authService.refreshAccessToken().pipe(
      switchMap((res) => {
        isRefreshing = false;
        refreshTokenSubject.next(res.accessToken);
        return next(addTokenHeader(request, res.accessToken));
      }),
      catchError((err) => {
        isRefreshing = false;
        refreshTokenSubject.next(null);
        authService.logout();
        return throwError(() => err);
      }),
    );
  } else {
    // If a refresh is already in flight, queue this request until the new token is available
    return refreshTokenSubject.pipe(
      filter((token) => token !== null),
      take(1),
      timeout(10000),
      switchMap((token) => next(addTokenHeader(request, token!))),
      catchError((err) => {
        authService.logout();
        return throwError(() => err);
      }),
    );
  }
}
