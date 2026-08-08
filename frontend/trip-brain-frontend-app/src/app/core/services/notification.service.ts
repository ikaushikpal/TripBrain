import { Injectable, signal } from '@angular/core';

export interface ToastMessage {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
  duration?: number;
}

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  toasts = signal<ToastMessage[]>([]);
  private nextId = 1;

  show(message: string, type: 'success' | 'error' | 'info' = 'info', duration: number = 4000) {
    const id = this.nextId++;
    const toast: ToastMessage = { id, message, type, duration };
    this.toasts.update((current) => [...current, toast]);

    setTimeout(() => {
      this.dismiss(id);
    }, duration);
  }

  success(message: string, duration?: number) {
    this.show(message, 'success', duration);
  }

  error(message: string, duration?: number) {
    this.show(message, 'error', duration);
  }

  info(message: string, duration?: number) {
    this.show(message, 'info', duration);
  }

  dismiss(id: number) {
    this.toasts.update((current) => current.filter((t) => t.id !== id));
  }
}
