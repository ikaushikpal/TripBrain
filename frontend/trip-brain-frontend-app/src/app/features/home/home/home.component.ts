import { Component, signal, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ChatService, PublicTrip } from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth.service';
import { ThemeService } from '../../../core/services/theme.service';

import { BASE_URL } from '../../../core/constants';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.component.html'
})
export class HomeComponent implements OnInit {
  readonly chatService = inject(ChatService);
  readonly authService = inject(AuthService);
  readonly themeService = inject(ThemeService);
  readonly baseUrl = BASE_URL;

  getAuthenticatedUrl(url: string | null | undefined): string {
    if (!url) return '';
    const token = this.authService.getAccessToken();
    return token ? `${this.baseUrl}${url}?token=${encodeURIComponent(token)}` : `${this.baseUrl}${url}`;
  }

  publicTrips = signal<PublicTrip[]>([]);
  isLoading = signal(true);

  ngOnInit() {
    this.loadPublicTrips();
  }

  loadPublicTrips() {
    this.isLoading.set(true);
    this.chatService.getPublicTrips().subscribe({
      next: (data) => {
        this.publicTrips.set(data.slice(0, 8)); // Display top 8 on landing page
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }

  toggleTheme() {
    this.themeService.toggleTheme();
  }

  isDark() {
    return this.themeService.darkMode();
  }
}
