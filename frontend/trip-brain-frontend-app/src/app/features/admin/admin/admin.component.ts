import { Component, signal, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavbarComponent } from '../../../shared/components/navbar/navbar.component';
import { AdminService } from '../../../core/services/admin.service';
import { User } from '../../../core/services/auth.service';

export interface AdminUser extends User {
  apiCallCount?: number;
  lastLoginAt?: string;
  blocked?: boolean;
  role: string;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, NavbarComponent],
  templateUrl: './admin.component.html'
})
export class AdminComponent implements OnInit {
  private readonly adminService = inject(AdminService);

  users = signal<AdminUser[]>([]);
  isLoading = signal(true);

  // Custom Confirmation Modal State
  showConfirmModal = signal(false);
  confirmTitle = signal('');
  confirmMessage = signal('');
  confirmCallback: (() => void) | null = null;

  openConfirm(title: string, message: string, callback: () => void) {
    this.confirmTitle.set(title);
    this.confirmMessage.set(message);
    this.confirmCallback = callback;
    this.showConfirmModal.set(true);
  }

  onConfirmAccept() {
    this.showConfirmModal.set(false);
    if (this.confirmCallback) {
      this.confirmCallback();
    }
  }

  onConfirmCancel() {
    this.showConfirmModal.set(false);
    this.confirmCallback = null;
  }

  activeCount = () => this.users().filter(u => !u.blocked).length;
  blockedCount = () => this.users().filter(u => u.blocked).length;
  adminCount = () => this.users().filter(u => u.role === 'ADMIN').length;

  ngOnInit() {
    this.loadUsers();
  }

  loadUsers() {
    this.isLoading.set(true);
    this.adminService.listAllUsers().subscribe({
      next: (data) => {
        this.users.set(data as AdminUser[]);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  toggleBlock(user: AdminUser) {
    this.adminService.toggleBlock(user.id, !user.blocked).subscribe({
      next: () => {
        this.users.update(list =>
          list.map(u => u.id === user.id ? { ...u, blocked: !u.blocked } : u)
        );
      }
    });
  }

  deleteUser(user: AdminUser) {
    this.openConfirm(
      "Delete User",
      `Are you sure you want to delete user "${user.name}"? This cannot be undone.`,
      () => {
        this.adminService.deleteUser(user.id).subscribe({
          next: () => {
            this.users.update(list => list.filter(u => u.id !== user.id));
          }
        });
      }
    );
  }
}
