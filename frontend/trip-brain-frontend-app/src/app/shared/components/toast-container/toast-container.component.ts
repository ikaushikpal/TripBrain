import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="fixed bottom-6 right-6 z-[9999] flex flex-col gap-3 max-w-sm w-full pointer-events-none"
    >
      @for (toast of notificationService.toasts(); track toast.id) {
        <div
          class="pointer-events-auto flex items-start gap-3 p-4 rounded-xl border backdrop-blur-md shadow-2xl transition-all duration-300 transform scale-100 hover:scale-[1.02] cursor-pointer animate-slide-in"
          [ngClass]="{
            'bg-emerald-950/90 border-emerald-500/30 text-emerald-300': toast.type === 'success',
            'bg-red-950/90 border-red-500/30 text-red-300': toast.type === 'error',
            'bg-blue-950/90 border-blue-500/30 text-blue-300': toast.type === 'info',
          }"
          (click)="notificationService.dismiss(toast.id)"
        >
          <!-- Success Icon -->
          @if (toast.type === 'success') {
            <svg
              class="w-5 h-5 shrink-0 text-emerald-400 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2.5"
                d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          }

          <!-- Error Icon -->
          @if (toast.type === 'error') {
            <svg
              class="w-5 h-5 shrink-0 text-red-400 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2.5"
                d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          }

          <!-- Info Icon -->
          @if (toast.type === 'info') {
            <svg
              class="w-5 h-5 shrink-0 text-blue-400 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2.5"
                d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          }

          <div class="flex-1 text-sm font-medium leading-tight">
            {{ toast.message }}
          </div>

          <button
            class="text-xs opacity-50 hover:opacity-100 transition-opacity shrink-0 ml-auto select-none"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      @keyframes slideIn {
        from {
          opacity: 0;
          transform: translateY(20px) scale(0.95);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }
      .animate-slide-in {
        animation: slideIn 0.25s cubic-bezier(0.16, 1, 0.3, 1) forwards;
      }
    `,
  ],
})
export class ToastContainerComponent {
  readonly notificationService = inject(NotificationService);
}
