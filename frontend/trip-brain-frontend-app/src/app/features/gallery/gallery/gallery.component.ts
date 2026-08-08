import { Component, signal, inject, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavbarComponent } from '../../../shared/components/navbar/navbar.component';
import { ChatService, PublicTrip } from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

import { BASE_URL } from '../../../core/constants';

@Component({
  selector: 'app-gallery',
  standalone: true,
  imports: [CommonModule, FormsModule, NavbarComponent],
  templateUrl: './gallery.component.html',
})
export class GalleryComponent implements OnInit {
  private readonly chatService = inject(ChatService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  readonly baseUrl = BASE_URL;

  getAuthenticatedUrl(url: string | null | undefined): string {
    if (!url) return '';
    const token = this.authService.getAccessToken();
    return token
      ? `${this.baseUrl}${url}?token=${encodeURIComponent(token)}`
      : `${this.baseUrl}${url}`;
  }

  trips = signal<PublicTrip[]>([]);
  searchQuery = signal('');
  isLoading = signal(true);
  forkingId = signal<string | null>(null);

  filtered = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    if (!q) return this.trips();
    return this.trips().filter(
      (t) => t.destination?.toLowerCase().includes(q) || t.tags?.toLowerCase().includes(q),
    );
  });

  ngOnInit() {
    this.loadTrips();
  }

  onSearch(q: string) {
    this.searchQuery.set(q);
  }

  loadTrips() {
    this.isLoading.set(true);
    this.chatService.getPublicTrips().subscribe({
      next: (data) => {
        this.trips.set(data);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false),
    });
  }

  forkTrip(trip: PublicTrip) {
    const userId = this.authService.currentUser()?.id;
    if (!userId) return;

    this.forkingId.set(trip.id);
    this.chatService.forkTrip(trip.id, userId).subscribe({
      next: () => {
        this.forkingId.set(null);
        this.notificationService.success(`Trip "${trip.destination}" copied to your dashboard!`);
      },
      error: () => this.forkingId.set(null),
    });
  }
}
