import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, AuthService } from './auth.service';

import { BASE_API_URL } from '../constants';

@Injectable({
  providedIn: 'root',
})
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly apiUrl = `${BASE_API_URL}/admin`;

  private getHeaders(): HttpHeaders {
    const adminId = this.auth.currentUser()?.id || '';
    return new HttpHeaders().set('X-Admin-Requester-Id', adminId);
  }

  listAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiUrl}/users`, { headers: this.getHeaders() });
  }

  toggleBlock(userId: string, block: boolean): Observable<string> {
    return this.http.post(
      `${this.apiUrl}/users/${userId}/block?block=${block}`,
      {},
      {
        headers: this.getHeaders(),
        responseType: 'text',
      },
    );
  }

  deleteUser(userId: string): Observable<string> {
    return this.http.delete(`${this.apiUrl}/users/${userId}`, {
      headers: this.getHeaders(),
      responseType: 'text',
    });
  }
}
