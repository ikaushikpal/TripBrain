import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { BASE_API_URL } from '../constants';

@Injectable({
  providedIn: 'root'
})
export class MapService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = BASE_API_URL;

  getMapRoute(conversationId: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/conversations/${conversationId}/map-route`);
  }
}
