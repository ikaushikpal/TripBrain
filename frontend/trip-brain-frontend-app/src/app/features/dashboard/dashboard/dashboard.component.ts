import { Component, signal, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { NavbarComponent } from '../../../shared/components/navbar/navbar.component';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { ChatWindowComponent } from '../chat-window/chat-window.component';
import { ChatService } from '../../../core/services/chat.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, NavbarComponent, SidebarComponent, ChatWindowComponent],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly chatService = inject(ChatService);
  private deleteSubscription?: Subscription;

  activeConversationId = signal<string | null>(null);
  sidebarOpen = signal(typeof window !== 'undefined' ? window.innerWidth > 768 : true);

  ngOnInit() {
    this.deleteSubscription = this.chatService.conversationDeleted$.subscribe((deletedId) => {
      if (!deletedId || this.activeConversationId() === deletedId) {
        this.activeConversationId.set(null);
      }
    });
  }

  onConversationSelected(id: string) {
    this.activeConversationId.set(id);
    if (typeof window !== 'undefined' && window.innerWidth <= 768) {
      this.sidebarOpen.set(false); // Only auto-close sidebar on mobile after selection
    }
  }

  toggleSidebar() {
    this.sidebarOpen.set(!this.sidebarOpen());
  }

  ngOnDestroy() {
    this.deleteSubscription?.unsubscribe();
  }
}
