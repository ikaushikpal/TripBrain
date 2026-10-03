import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';

export interface User {
  id: string;
  email: string;
  name: string;
  role: string;
  blocked?: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

import { BASE_API_URL } from '../constants';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly apiUrl = BASE_API_URL;

  readonly currentUser = signal<User | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  constructor() {
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem('trip_brain_user');
      if (stored) {
        try {
          this.currentUser.set(JSON.parse(stored));
        } catch (e) {
          this.clearSession();
        }
      }
    }
  }

  getAccessToken(): string | null {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('trip_brain_accessToken');
    }
    return null;
  }

  getRefreshToken(): string | null {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('trip_brain_refreshToken');
    }
    return null;
  }

  login(credentials: { email: string; password: string }): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/login`, credentials).pipe(
      tap((res) => {
        this.saveSession(res);
      }),
    );
  }

  refreshAccessToken(): Observable<LoginResponse> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      this.logout();
      return throwError(() => new Error('No refresh token available'));
    }
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/refresh`, { refreshToken }).pipe(
      tap((res) => {
        this.saveSession(res);
      }),
      catchError((err) => {
        // If refresh token is invalid or expired in backend, perform a full logout
        this.logout();
        return throwError(() => err);
      }),
    );
  }

  private saveSession(res: LoginResponse) {
    this.currentUser.set(res.user);
    if (typeof window !== 'undefined') {
      localStorage.setItem('trip_brain_user', JSON.stringify(res.user));
      localStorage.setItem('trip_brain_accessToken', res.accessToken);
      localStorage.setItem('trip_brain_refreshToken', res.refreshToken);
    }
  }

  private clearSession() {
    this.currentUser.set(null);
    if (typeof window !== 'undefined') {
      localStorage.removeItem('trip_brain_user');
      localStorage.removeItem('trip_brain_accessToken');
      localStorage.removeItem('trip_brain_refreshToken');
      localStorage.removeItem('trip_brain_cached_conversations');

      // Thoroughly clear all cached conversation message entries
      const keysToRemove: string[] = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith('trip_brain_cached_messages_')) {
          keysToRemove.push(key);
        }
      }
      keysToRemove.forEach((k) => localStorage.removeItem(k));
    }
  }

  register(user: { name: string; email: string; password: string }): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/users`, user);
  }

  logout(redirectUrl: string = '/auth/login') {
    this.clearSession();
    this.router.navigate([redirectUrl]);
  }
}
