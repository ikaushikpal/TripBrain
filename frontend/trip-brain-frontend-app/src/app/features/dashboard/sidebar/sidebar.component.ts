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
  
  // Advanced optional settings
  prefCabinClass = 'ECONOMY';
  prefDirectFlightsOnly = false;
  prefPrivateTransfer = false;
  prefIncludeFoodTour = false;
  prefActivityIntensity = 'MODERATE';
  prefNationality = '';
  prefAccessibility = false;
  prefMaxTravelTime = 4;
  prefMustVisit = '';
  prefAvoid = '';

  // Subgroup collapse states
  showTransportGroup = signal(false);
  showHotelGroup = signal(false);
  showDiningGroup = signal(false);
  showProfileGroup = signal(false);
  showPlacesGroup = signal(false);

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
          maxBudget: this.prefBudget,
          travellerType: this.prefTravelerType,
          budgetPreference: this.prefBudgetClass,
          headcount: this.prefHeadcount,
          currency: this.prefCurrency,
          adults: this.prefAdults,
          children: this.prefChildren,
          minHotelStars: this.prefMinHotelStars,
          maxHotelStars: this.prefMaxHotelStars,
          notes: this.prefNotes,
          mustVisitPlaces: this.prefMustVisit ? this.prefMustVisit.split(',').map(s => s.trim()).filter(Boolean) : [],
          avoidPlaces: this.prefAvoid ? this.prefAvoid.split(',').map(s => s.trim()).filter(Boolean) : [],
          vacationStyles: [],
          interests: [],
          preferredTransportModes: [],
          requiredAmenities: [],
          includeHotels: true,
          includeTransport: true,
          includeWeatherForecast: true,
          generateWeatherFallbacks: true,
          cabinClass: this.prefCabinClass,
          directFlightsOnly: this.prefDirectFlightsOnly,
          privateTransferPreferred: this.prefPrivateTransfer,
          includeFoodTour: this.prefIncludeFoodTour,
          activityIntensity: this.prefActivityIntensity,
          nationality: this.prefNationality,
          accessibilityRequired: this.prefAccessibility,
          maxTravelTimePerDay: this.prefMaxTravelTime
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
    this.prefCabinClass = 'ECONOMY';
    this.prefDirectFlightsOnly = false;
    this.prefPrivateTransfer = false;
    this.prefIncludeFoodTour = false;
    this.prefActivityIntensity = 'MODERATE';
    this.prefNationality = '';
    this.prefAccessibility = false;
    this.prefMaxTravelTime = 4;
    this.prefMustVisit = '';
    this.prefAvoid = '';
    this.showTransportGroup.set(false);
    this.showHotelGroup.set(false);
    this.showDiningGroup.set(false);
    this.showProfileGroup.set(false);
    this.showPlacesGroup.set(false);
  }

  onHeadcountChange() {
    if (this.prefHeadcount < 1) this.prefHeadcount = 1;
    
    // Suggest traveler type based on headcount
    if (this.prefHeadcount === 1) {
      this.prefTravelerType = 'SOLO';
    } else if (this.prefHeadcount === 2) {
      if (this.prefTravelerType !== 'COUPLE' && this.prefTravelerType !== 'HONEYMOON') {
        this.prefTravelerType = 'COUPLE';
      }
    } else {
      if (this.prefTravelerType !== 'FAMILY_WITH_KIDS' && this.prefTravelerType !== 'GROUP_FRIENDS') {
        this.prefTravelerType = 'FAMILY_WITH_KIDS';
      }
    }

    // Set initial adults/children count to match headcount
    this.prefAdults = this.prefHeadcount;
    this.prefChildren = 0;
  }

  onAdultsChange() {
    if (this.prefAdults < 1) this.prefAdults = 1;
    if (this.prefAdults > this.prefHeadcount) this.prefAdults = this.prefHeadcount;
    this.prefChildren = this.prefHeadcount - this.prefAdults;
  }

  onChildrenChange() {
    if (this.prefChildren < 0) this.prefChildren = 0;
    if (this.prefChildren >= this.prefHeadcount) this.prefChildren = this.prefHeadcount - 1;
    this.prefAdults = this.prefHeadcount - this.prefChildren;
  }
}
