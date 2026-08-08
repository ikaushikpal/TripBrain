import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  darkMode = signal(true); // Default to dark mode (aesthetics)

  toggleTheme() {
    // Theme toggling is disabled as theme toggler is removed
  }

  applyTheme() {
    if (typeof document !== 'undefined') {
      const htmlEl = document.documentElement;
      htmlEl.classList.add('dark');
      try {
        if (
          typeof localStorage !== 'undefined' &&
          localStorage &&
          typeof localStorage.setItem === 'function'
        ) {
          localStorage.setItem('theme', 'dark');
        }
      } catch (e) {
        // Ignore storage errors in SSR or test runner environment
      }
    }
  }

  initTheme() {
    if (typeof window !== 'undefined') {
      this.darkMode.set(true);
      this.applyTheme();
    }
  }
}
