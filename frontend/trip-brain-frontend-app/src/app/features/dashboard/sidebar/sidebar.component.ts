import { Component, signal, inject, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService, Conversation } from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sidebar.component.html'
})
export class SidebarComponent implements OnInit {
  @Input() isOpen = false;
  @Output() conversationSelected = new EventEmitter<string>();
  @Output() closeSidebar = new EventEmitter<void>();

  private readonly chatService = inject(ChatService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);

  conversations = signal<Conversation[]>([]);
  selectedId = signal<string | null>(null);
  isLoading = signal(false);
  isCreating = signal(false);

  // Preference modal signals and state
  showPreferencesModal = signal(false);
  prefSource = '';
  prefDestination = '';
  prefStartDate = '';
  prefEndDate = '';
  prefBudget: number | null = 100000;
  prefHeadcount = 2;
  prefBudgetClass = 'MID';
  prefTravelerType = 'COUPLE';
  prefCurrency = 'INR';

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

  // Optional settings
  showOptional = false;
  prefAdults = 2;
  prefChildren = 0;
  prefMinHotelStars = 3;
  prefMaxHotelStars = 5;
  prefNotes = '';

  get minStartDate(): string {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  ngOnInit() {
    this.loadConversations();
    this.chatService.conversationUpdated$.subscribe(() => {
      this.loadConversations();
    });
  }

  loadConversations() {
    this.isLoading.set(true);
    this.chatService.getUserConversations().subscribe({
      next: (list) => {
        this.conversations.set(list || []);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }

  selectConversation(id: string) {
    this.selectedId.set(id);
    this.conversationSelected.emit(id);
  }

  newConversation() {
    // Show preferences modal first
    this.showPreferencesModal.set(true);
  }

  startTripWithPreferences(event: Event) {
    event.preventDefault();
    
    // Validations
    if (!this.prefSource || !this.prefDestination || !this.prefStartDate || !this.prefEndDate) {
      this.notificationService.error("Please fill in all required fields (Source, Destination, Start Date, End Date).");
      return;
    }
    if (new Date(this.prefEndDate) < new Date(this.prefStartDate)) {
      this.notificationService.error("End date must be on or after the start date.");
      return;
    }
    if (this.prefBudget !== null && this.prefBudget <= 0) {
      this.notificationService.error("Budget must be a positive number.");
      return;
    }
    if (this.prefHeadcount <= 0) {
      this.notificationService.error("Travelers count must be at least 1.");
      return;
    }
    if (this.prefAdults < 1) {
      this.notificationService.error("Adults count must be at least 1.");
      return;
    }

    const userId = this.authService.currentUser()?.id;
    if (!userId) return;

    this.isCreating.set(true);

    // 1. Create the new conversation record on backend
    this.chatService.startNewConversation(userId).subscribe({
      next: (conv) => {
        // 2. Call the uploadPreferences endpoint to seed initial state and trigger LLM start
        const preferences = {
          source: this.prefSource,
          destination: this.prefDestination,
          startDate: this.prefStartDate,
          endDate: this.prefEndDate,
          maxBudgetInr: this.prefBudget,
          travellerType: this.prefTravelerType,
          budgetPreference: this.prefBudgetClass,
          headcount: this.prefHeadcount,
          currency: this.prefCurrency,
          adults: this.prefAdults,
          children: this.prefChildren,
          minHotelStars: this.prefMinHotelStars,
          maxHotelStars: this.prefMaxHotelStars,
          notes: this.prefNotes,
          mustVisitPlaces: [],
          vacationStyles: [],
          interests: [],
          preferredTransportModes: [],
          requiredAmenities: [],
          includeHotels: true,
          includeTransport: true,
          includeWeatherForecast: true,
          generateWeatherFallbacks: true
        };

        this.chatService.uploadPreferences(conv.id, preferences).subscribe({
          next: () => {
            this.chatService.conversationUpdated$.emit();
            this.isCreating.set(false);
            this.showPreferencesModal.set(false);
            this.selectConversation(conv.id);
            this.resetForm();
          },
          error: () => {
            // Even if preferences call fails, show the conversation
            this.conversations.update(list => [conv, ...list]);
            this.isCreating.set(false);
            this.showPreferencesModal.set(false);
            this.selectConversation(conv.id);
          }
        });
      },
      error: () => {
        this.isCreating.set(false);
      }
    });
  }

  deleteConversation(id: string, event: Event) {
    event.stopPropagation();
    this.openConfirm(
      "Delete Conversation",
      "Are you sure you want to delete this conversation?",
      () => {
        this.chatService.deleteConversation(id).subscribe({
          next: () => {
            if (this.selectedId() === id) {
              this.selectedId.set(null);
              this.conversationSelected.emit('');
            }
            this.loadConversations();
          },
          error: (err) => {
            console.error("Failed to delete conversation", err);
            this.notificationService.error("Could not delete conversation. Please try again.");
          }
        });
      }
    );
  }

  togglePin(id: string, currentPinned: boolean | undefined, event: Event) {
    event.stopPropagation();
    const newPinned = !currentPinned;
    this.chatService.togglePin(id, newPinned).subscribe({
      next: () => {
        this.loadConversations();
      },
      error: (err) => {
        console.error("Failed to toggle pin", err);
      }
    });
  }

  private resetForm() {
    this.prefSource = '';
    this.prefDestination = '';
    this.prefStartDate = '';
    this.prefEndDate = '';
    this.prefBudget = 100000;
    this.prefHeadcount = 2;
    this.prefBudgetClass = 'MID';
    this.prefTravelerType = 'COUPLE';
    this.prefCurrency = 'INR';
    this.prefAdults = 2;
    this.prefChildren = 0;
    this.prefMinHotelStars = 3;
    this.prefMaxHotelStars = 5;
    this.prefNotes = '';
    this.showOptional = false;
  }
}
