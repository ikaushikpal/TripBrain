import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpEvent, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { Observable, throwError, BehaviorSubject, of } from 'rxjs';
import { catchError, switchMap, filter, take } from 'rxjs/operators';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (typeof window === 'undefined') {
    // Return empty list by default for GET list endpoints, otherwise empty object
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
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !req.url.includes('/auth/login') &&
        !req.url.includes('/auth/refresh')
      ) {
        return handle401Error(authReq, next, authService);
      }
      return throwError(() => error);
    })
  );
};

function addTokenHeader(request: HttpRequest<any>, token: string) {
  return request.clone({
    headers: request.headers.set('Authorization', 'Bearer ' + token)
  });
}

function handle401Error(request: HttpRequest<any>, next: HttpHandlerFn, authService: AuthService): Observable<HttpEvent<any>> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshTokenSubject.next(null);

    return authService.refreshAccessToken().pipe(
      switchMap((res) => {
        isRefreshing = false;
        refreshTokenSubject.next(res.accessToken);
        return next(addTokenHeader(request, res.accessToken));
      }),
      catchError((err) => {
        isRefreshing = false;
        authService.logout();
        return throwError(() => err);
      })
    );
  } else {
    return refreshTokenSubject.pipe(
      filter(token => token !== null),
      take(1),
      switchMap((token) => next(addTokenHeader(request, token!)))
    );
  }
}
