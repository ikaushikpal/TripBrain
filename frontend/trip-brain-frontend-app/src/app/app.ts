import { Component, OnInit, inject, signal, HostListener } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/services/theme.service';
import { ToastContainerComponent } from './shared/components/toast-container/toast-container.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastContainerComponent],
  template: `
    @if (isOffline()) {
      <div
        class="bg-red-600 text-white text-center py-2 text-xs font-semibold uppercase tracking-wider relative z-[9999] animate-fadeIn flex items-center justify-center gap-2"
      >
        <svg class="w-4 h-4 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M18.364 5.636a9 9 0 010 12.728m0 0l-2.829-2.829m2.829 2.829L21 21M15.536 8.464a5 5 0 010 7.072m0 0l-2.829-2.829m-4.243 2.829a4.978 4.978 0 01-1.414-3.536 4.978 4.978 0 011.414-3.536M4 4h16v16H4V4z"
          />
        </svg>
        You are currently offline. Some features may be unavailable.
      </div>
    }
    <router-outlet />
    <app-toast-container />
  `,
})
export class App implements OnInit {
  private readonly themeService = inject(ThemeService);
  isOffline = signal(!navigator.onLine);

  @HostListener('window:online')
  onOnline() {
    this.isOffline.set(false);
  }

  @HostListener('window:offline')
  onOffline() {
    this.isOffline.set(true);
  }

  ngOnInit() {
    this.themeService.initTheme();
  }
}
