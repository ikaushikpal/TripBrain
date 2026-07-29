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

  onConversationSelected(id: string) {
    this.activeConversationId.set(id);
  }
}
