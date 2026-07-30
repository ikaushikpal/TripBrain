import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavbarComponent } from '../../../shared/components/navbar/navbar.component';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { ChatWindowComponent } from '../chat-window/chat-window.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, NavbarComponent, SidebarComponent, ChatWindowComponent],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent {
  activeConversationId = signal<string | null>(null);
  sidebarOpen = signal(typeof window !== 'undefined' ? window.innerWidth > 768 : true);

  onConversationSelected(id: string) {
    this.activeConversationId.set(id);
    if (typeof window !== 'undefined' && window.innerWidth <= 768) {
      this.sidebarOpen.set(false); // Only auto-close sidebar on mobile after selection
    }
  }

  toggleSidebar() {
    this.sidebarOpen.set(!this.sidebarOpen());
  }
}
