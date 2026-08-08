import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { tap } from 'rxjs/operators';

import { BASE_API_URL } from '../constants';

@Injectable({
  providedIn: 'root',
})
export class MapService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = BASE_API_URL;
  private readonly routeCache = new Map<string, any>();

  getMapRoute(conversationId: string): Observable<any> {
    if (this.routeCache.has(conversationId)) {
      return of(this.routeCache.get(conversationId));
    }
    return this.http
      .get<any>(`${this.apiUrl}/conversations/${conversationId}/map-route`)
      .pipe(tap((data) => this.routeCache.set(conversationId, data)));
  }

  clearCache(conversationId: string) {
    this.routeCache.delete(conversationId);
  }
}
