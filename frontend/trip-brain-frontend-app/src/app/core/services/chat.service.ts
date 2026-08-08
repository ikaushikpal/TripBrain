import { Injectable, inject, EventEmitter } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, of } from 'rxjs';
import { AuthService } from './auth.service';

export interface Conversation {
  id: string;
  title?: string;
  createdAt: string;
  isPublic?: boolean;
  pinned?: boolean;
}

export interface ChatMessage {
  role: 'USER' | 'ASSISTANT';
  content: string;
  timestamp: string;
  messageType?: string; // e.g. TEXT, WIDGET
  metadata?: string; // JSON string containing rich card structures
}

export interface ConversationHistory {
  conversation: Conversation;
  messages: ChatMessage[];
}

export interface PublicTrip {
  id: string;
  destination: string;
  publicUrl: string;
  thumbnailUrl?: string;
  generatedAt: string;
  tags?: string;
}

import { BASE_API_URL } from '../constants';

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly apiUrl = BASE_API_URL;
  public readonly conversationUpdated$ = new EventEmitter<void>();
  public readonly conversationDeleted$ = new EventEmitter<string>();

  getUserConversations(): Observable<Conversation[]> {
    const cachedStr =
      typeof window !== 'undefined'
        ? localStorage.getItem('trip_brain_cached_conversations')
        : null;
    const cachedList: Conversation[] = cachedStr ? JSON.parse(cachedStr) : [];

    const httpCall = this.http.get<Conversation[]>(`${this.apiUrl}/conversations`).pipe(
      tap((list) => {
        if (typeof window !== 'undefined' && list) {
          localStorage.setItem(
            'trip_brain_cached_conversations',
            JSON.stringify(list.slice(0, 10)),
          );
        }
      }),
    );

    if (cachedList.length > 0) {
      return new Observable<Conversation[]>((observer) => {
        observer.next(cachedList);
        const sub = httpCall.subscribe({
          next: (list) => observer.next(list),
          error: (err) => observer.error(err),
          complete: () => observer.complete(),
        });
        return () => sub.unsubscribe();
      });
    } else {
      return httpCall;
    }
  }

  startNewConversation(userId: string): Observable<Conversation> {
    return this.http.post<Conversation>(`${this.apiUrl}/conversations/new`, { userId });
  }

  getConversationMessages(
    conversationId: string,
    cursor?: number | null,
    limit?: number,
  ): Observable<ConversationHistory & { nextCursor?: string }> {
    let url = `${this.apiUrl}/conversations/${conversationId}/messages?limit=${limit || 15}`;
    if (cursor !== undefined && cursor !== null) {
      url += `&cursor=${cursor}`;
    }
    return this.http.get<ConversationHistory & { nextCursor?: string }>(url);
  }

  getCachedMessages(conversationId: string): any[] {
    if (typeof window === 'undefined') return [];
    const stored = localStorage.getItem(`trip_brain_cached_messages_${conversationId}`);
    return stored ? JSON.parse(stored) : [];
  }

  cacheMessages(conversationId: string, messages: any[]): void {
    if (typeof window === 'undefined') return;
    const slice = messages.slice(-15);
    localStorage.setItem(`trip_brain_cached_messages_${conversationId}`, JSON.stringify(slice));
  }

  deleteConversation(conversationId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/conversations/${conversationId}`);
  }

  uploadPdf(conversationId: string, file: File): Observable<string> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.apiUrl}/conversations/${conversationId}/upload`, formData, {
      responseType: 'text',
    });
  }

  uploadPreferences(conversationId: string, preferences: any): Observable<string> {
    return this.http.post(`${this.apiUrl}/chat/${conversationId}/uploadPreferences`, preferences, {
      responseType: 'text',
    });
  }

  exportPdf(conversationId: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/chat/${conversationId}/export`, {});
  }

  getPublicTrips(): Observable<PublicTrip[]> {
    return this.http.get<PublicTrip[]>(`${this.apiUrl}/conversations/trips/public`);
  }

  forkTrip(pdfId: string, targetUserId: string): Observable<Conversation> {
    return this.http.post<Conversation>(
      `${this.apiUrl}/conversations/trips/${pdfId}/fork?targetUserId=${targetUserId}`,
      {},
    );
  }

  updateVisibility(pdfId: string, isPublic: boolean): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/conversations/trips/${pdfId}/visibility?isPublic=${isPublic}`,
      {},
    );
  }

  getPresignedUploadUrl(
    conversationId: string,
    filename: string,
    contentType: string,
  ): Observable<{ uploadUrl: string; fileKey: string }> {
    return this.http.get<{ uploadUrl: string; fileKey: string }>(
      `${this.apiUrl}/conversations/${conversationId}/upload-url?filename=${encodeURIComponent(filename)}&contentType=${encodeURIComponent(contentType)}`,
    );
  }

  uploadToPresignedUrl(uploadUrl: string, file: File): Observable<any> {
    return this.http.put(uploadUrl, file, {
      headers: { 'Content-Type': file.type },
    });
  }

  processUpload(
    conversationId: string,
    fileKey: string,
    contentType: string,
    filename: string,
  ): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/conversations/${conversationId}/process-upload`, {
      fileKey,
      contentType,
      filename,
    });
  }

  getDestinationImage(conversationId: string): Observable<{ imageUrl: string }> {
    return this.http.get<{ imageUrl: string }>(
      `${this.apiUrl}/conversations/${conversationId}/destination-image`,
    );
  }

  togglePin(conversationId: string, pinned: boolean): Observable<void> {
    return this.http.put<void>(
      `${this.apiUrl}/conversations/${conversationId}/pin?pinned=${pinned}`,
      {},
    );
  }

  togglePublic(conversationId: string, isPublic: boolean): Observable<void> {
    return this.http.put<void>(
      `${this.apiUrl}/conversations/${conversationId}/public?isPublic=${isPublic}`,
      {},
    );
  }

  getSharedConversationMessages(
    conversationId: string,
    cursor?: number | null,
    limit?: number,
  ): Observable<ConversationHistory & { nextCursor?: string }> {
    let url = `${this.apiUrl}/conversations/share/${conversationId}?limit=${limit || 15}`;
    if (cursor !== undefined && cursor !== null) {
      url += `&cursor=${cursor}`;
    }
    return this.http.get<ConversationHistory & { nextCursor?: string }>(url);
  }

  getTripRequest(conversationId: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/conversations/${conversationId}/trip-request`);
  }

  updateTripRequest(conversationId: string, dto: any): Observable<any> {
    return this.http.patch<any>(`${this.apiUrl}/conversations/${conversationId}/trip-request`, dto);
  }

  // Reactive Server-Sent Events (SSE) Stream Subscriber
  getChatStream(
    conversationId: string,
    message: string,
  ): Observable<{ event: string; data: string }> {
    return new Observable((observer) => {
      // In Server Side Rendering (SSR), EventSource doesn't exist, so execute client-side only
      if (typeof window === 'undefined') {
        observer.complete();
        return;
      }

      const token = this.authService.getAccessToken();
      const url =
        `${this.apiUrl}/chat/${conversationId}/stream?message=${encodeURIComponent(message)}` +
        (token ? `&token=${encodeURIComponent(token)}` : '');
      const eventSource = new EventSource(url);

      eventSource.addEventListener('status', (e: MessageEvent) => {
        observer.next({ event: 'status', data: e.data });
      });

      eventSource.addEventListener('text', (e: MessageEvent) => {
        try {
          const parsed = JSON.parse(e.data);
          observer.next({
            event: 'text',
            data: parsed.content !== undefined ? parsed.content : e.data,
          });
        } catch {
          observer.next({ event: 'text', data: e.data });
        }
      });

      eventSource.addEventListener('error', (e) => {
        if (eventSource.readyState === EventSource.CLOSED) {
          observer.complete();
        } else {
          observer.error(e);
        }
      });

      return () => {
        eventSource.close();
      };
    });
  }

  getDownloadUrl(conversationId: string): Observable<{ downloadUrl: string }> {
    return this.http.get<{ downloadUrl: string }>(
      `${this.apiUrl}/conversations/trips/${conversationId}/download-url`,
    );
  }
}
