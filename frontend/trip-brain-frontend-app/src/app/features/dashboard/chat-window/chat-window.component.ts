import {
  Component,
  signal,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  SimpleChanges,
  inject,
  ViewChild,
  ElementRef,
  AfterViewChecked,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ChatService, ChatMessage } from '../../../core/services/chat.service';
import { NotificationService } from '../../../core/services/notification.service';
import { MapOverlayComponent } from '../../../shared/components/map-overlay/map-overlay.component';
import { MapService } from '../../../core/services/map.service';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import { BASE_URL } from '../../../core/constants';

export interface AssistantBuffer {
  role: 'ASSISTANT';
  content: string;
  isStreaming: boolean;
}

@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule, MapOverlayComponent],
  templateUrl: './chat-window.component.html',
  styleUrl: './chat-window.component.css',
})
export class ChatWindowComponent implements OnInit, OnChanges, AfterViewChecked, OnDestroy {
  @Input() conversationId: string | null = null;
  @Input() isSidebarOpen = false;
  @Output() toggleSidebar = new EventEmitter<void>();
  @ViewChild('messagesContainer') messagesContainer!: ElementRef;

  private readonly chatService = inject(ChatService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly mapService = inject(MapService);

  messages = signal<(ChatMessage | AssistantBuffer)[]>([]);
  streamStatus = signal<string | null>(null);
  isStreaming = signal(false);
  isExporting = signal(false);
  showMap = signal(false);
  showScrollBottomButton = signal(false);
  inputMessage = '';
  private shouldScrollToBottom = false;
  private activeStreamSubscription?: Subscription;

  // New signals for wallpaper and file upload
  wallpaperUrl = signal<string | null>(null);
  isFileUploading = signal(false);
  uploadError = signal<string | null>(null);
  uploadProgressText = signal<string>('');

  // Dropdown control
  showHamburgerMenu = signal(false);

  // Active Conversation Info
  currentConversation = signal<any | null>(null);

  // Details Modal variables
  showDetailsModal = signal(false);
  detailsSource = '';
  detailsDestination = '';
  detailsStartDate = '';
  detailsEndDate = '';
  detailsBudget: number | null = 100000;
  detailsHeadcount = 2;
  detailsBudgetClass = 'MID';
  detailsTravelerType = 'COUPLE';
  detailsAdults = 2;
  detailsChildren = 0;
  detailsMinHotelStars = 3;
  detailsMaxHotelStars = 5;
  detailsNotes = '';
  detailsCurrency = 'INR';

  // Advanced optional fields
  detailsCabinClass = 'ECONOMY';
  detailsDirectFlightsOnly = false;
  detailsPrivateTransfer = false;
  detailsIncludeFoodTour = false;
  detailsActivityIntensity = 'MODERATE';
  detailsNationality = '';
  detailsAccessibility = false;
  detailsMaxTravelTime = 4;
  detailsMustVisit = '';
  detailsAvoid = '';

  // Expandable UI sections in Details Modal
  showDetailsTransportGroup = signal(false);
  showDetailsHotelGroup = signal(false);
  showDetailsDiningGroup = signal(false);
  showDetailsActivitiesGroup = signal(false);
  showDetailsProfileGroup = signal(false);
  showDetailsPlacesGroup = signal(false);

  // Confirm Modal
  showConfirmModal = signal(false);
  confirmTitle = signal('');
  confirmMessage = signal('');
  confirmAction?: () => void;

  // Infinite Scroll Pagination
  nextCursor: number | null = null;
  isLoadingMore = signal(false);

  ngOnInit() {
    this.loadMessages();
    this.loadWallpaper();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['conversationId']) {
      this.stopStreaming();
      this.loadMessages();
      this.loadWallpaper();
      this.showHamburgerMenu.set(false);
    }
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  ngOnDestroy() {
    this.stopStreaming();
  }

  onScroll(event: Event) {
    const el = event.target as HTMLElement;
    if (!el) return;

    // Show scroll bottom button if scrolled up > 150px
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    this.showScrollBottomButton.set(distanceFromBottom > 150);

    // Infinite scroll up
    if (el.scrollTop < 80 && !this.isLoadingMore() && this.nextCursor !== null) {
      this.loadMoreMessages();
    }
  }

  scrollToBottom() {
    try {
      if (this.messagesContainer?.nativeElement) {
        const el = this.messagesContainer.nativeElement;
        el.scrollTo({
          top: el.scrollHeight,
          behavior: 'smooth',
        });
      }
    } catch (e) {}
  }

  loadMoreMessages() {
    if (!this.conversationId || this.nextCursor === null || this.isLoadingMore()) return;

    this.isLoadingMore.set(true);
    const container = this.messagesContainer.nativeElement;
    const oldScrollHeight = container.scrollHeight;
    const oldScrollTop = container.scrollTop;

    this.chatService.getConversationMessages(this.conversationId, this.nextCursor, 15).subscribe({
      next: (res) => {
        const oldMsgs = this.messages();
        const newMsgs = res.messages || [];
        this.messages.set([...newMsgs, ...oldMsgs]);
        this.nextCursor = res.nextCursor ? parseInt(res.nextCursor) : null;
        this.isLoadingMore.set(false);

        setTimeout(() => {
          const newScrollHeight = container.scrollHeight;
          container.scrollTop = newScrollHeight - oldScrollHeight + oldScrollTop;
        }, 0);
      },
      error: () => {
        this.isLoadingMore.set(false);
      },
    });
  }

  loadMessages() {
    if (!this.conversationId) {
      this.currentConversation.set(null);
      this.messages.set([]);
      this.nextCursor = null;
      return;
    }

    const cached = this.chatService.getCachedMessages(this.conversationId);
    if (cached && cached.length > 0) {
      this.messages.set(cached);
      this.shouldScrollToBottom = true;
      this.fetchLatestPageInBackground();
    } else {
      this.loadMessagesPage(null);
    }
  }

  loadMessagesPage(cursor: number | null) {
    if (!this.conversationId) return;
    this.chatService.getConversationMessages(this.conversationId, cursor, 15).subscribe({
      next: (data) => {
        this.currentConversation.set(data.conversation);
        this.messages.set(data.messages || []);
        this.nextCursor = data.nextCursor ? parseInt(data.nextCursor) : null;
        this.shouldScrollToBottom = true;
        this.chatService.cacheMessages(this.conversationId!, data.messages || []);
      },
      error: () => {},
    });
  }

  fetchLatestPageInBackground() {
    if (!this.conversationId) return;
    this.chatService.getConversationMessages(this.conversationId, null, 15).subscribe({
      next: (data) => {
        this.currentConversation.set(data.conversation);
        this.messages.set(data.messages || []);
        this.nextCursor = data.nextCursor ? parseInt(data.nextCursor) : null;
        this.chatService.cacheMessages(this.conversationId!, data.messages || []);
      },
      error: () => {},
    });
  }

  loadWallpaper() {
    if (!this.conversationId) {
      this.wallpaperUrl.set(null);
      return;
    }
    this.chatService.getDestinationImage(this.conversationId).subscribe({
      next: (res) => {
        this.wallpaperUrl.set(res.imageUrl);
      },
      error: () => {
        this.wallpaperUrl.set(null);
      },
    });
  }

  togglePublicState() {
    const conv = this.currentConversation();
    if (!conv) return;
    const currentState = !!conv.isPublic;
    const nextState = !currentState;

    if (nextState) {
      this.openConfirm(
        'Make Conversation Public',
        'Are you sure you want to make this conversation public? Anyone with the shareable link will be able to view its contents.',
        () => this.executeTogglePublic(conv.id, nextState),
      );
    } else {
      this.executeTogglePublic(conv.id, nextState);
    }
  }

  private executeTogglePublic(convId: string, nextState: boolean) {
    const conv = this.currentConversation();
    if (!conv) return;
    this.chatService.togglePublic(convId, nextState).subscribe({
      next: () => {
        conv.isPublic = nextState;
        this.currentConversation.set({ ...conv });
        this.showHamburgerMenu.set(false);
        this.chatService.conversationUpdated$.emit();
        this.notificationService.success(
          `Conversation visibility updated to: ${nextState ? 'Public' : 'Private'}`,
        );
      },
      error: () => {
        this.notificationService.error('Failed to update conversation visibility.');
      },
    });
  }

  shareConversationLink() {
    const conv = this.currentConversation();
    if (!conv) return;

    if (!conv.isPublic) {
      this.openConfirm(
        'Make Conversation Public',
        'This conversation must be public in order to share it. Make it public now?',
        () => {
          this.chatService.togglePublic(conv.id, true).subscribe({
            next: () => {
              conv.isPublic = true;
              this.currentConversation.set({ ...conv });
              this.copyShareLink(conv.id);
              this.chatService.conversationUpdated$.emit();
            },
            error: () => {
              this.notificationService.error('Failed to make conversation public.');
            },
          });
        },
      );
    } else {
      this.copyShareLink(conv.id);
    }
  }

  private copyShareLink(convId: string) {
    const shareUrl = `${window.location.origin}/share/${convId}`;
    navigator.clipboard
      .writeText(shareUrl)
      .then(() => {
        this.notificationService.success('Shareable trip plan link copied to clipboard!');
        this.showHamburgerMenu.set(false);
      })
      .catch(() => {
        this.notificationService.error('Failed to copy link.');
      });
  }

  openDetailsModal() {
    if (!this.conversationId) return;
    this.chatService.getTripRequest(this.conversationId).subscribe({
      next: (req) => {
        this.detailsSource = req.source || '';
        this.detailsDestination = req.destination || '';
        this.detailsStartDate = req.startDate || '';
        this.detailsEndDate = req.endDate || '';
        this.detailsBudget = req.maxBudget || 100000;
        this.detailsHeadcount = req.headcount || 2;
        this.detailsBudgetClass = req.budgetPreference || 'MID';
        this.detailsTravelerType = req.travellerType || 'COUPLE';
        this.detailsAdults = req.adults || 2;
        this.detailsChildren = req.children || 0;
        this.detailsMinHotelStars = req.minHotelStars || 3;
        this.detailsMaxHotelStars = req.maxHotelStars || 5;
        this.detailsNotes = req.notes || '';
        this.detailsCurrency = req.currency || 'INR';

        // Advanced optional fields
        this.detailsCabinClass = req.cabinClass || 'ECONOMY';
        this.detailsDirectFlightsOnly = req.directFlightsOnly || false;
        this.detailsPrivateTransfer = req.privateTransferPreferred || false;
        this.detailsIncludeFoodTour = req.includeFoodTour || false;
        this.detailsActivityIntensity = req.activityIntensity || 'MODERATE';
        this.detailsNationality = req.nationality || '';
        this.detailsAccessibility = req.accessibilityRequired || false;
        this.detailsMaxTravelTime = req.maxTravelTimePerDay || 4;
        this.detailsMustVisit = req.mustVisitPlaces ? req.mustVisitPlaces.join(', ') : '';
        this.detailsAvoid = req.avoidPlaces ? req.avoidPlaces.join(', ') : '';

        this.showDetailsTransportGroup.set(false);
        this.showDetailsHotelGroup.set(false);
        this.showDetailsDiningGroup.set(false);
        this.showDetailsActivitiesGroup.set(false);

        this.showDetailsModal.set(true);
        this.showHamburgerMenu.set(false);
      },
      error: () => {
        this.notificationService.error('Failed to load travel preferences.');
      },
    });
  }

  saveTripDetails(event: Event) {
    event.preventDefault();
    if (!this.conversationId) return;

    const parseList = (str: string) =>
      str
        ? str
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean)
        : [];

    const updatePayload = {
      source: this.detailsSource,
      destination: this.detailsDestination,
      startDate: this.detailsStartDate,
      endDate: this.detailsEndDate,
      maxBudget: this.detailsBudget || 100000,
      headcount: this.detailsHeadcount,
      budgetPreference: this.detailsBudgetClass,
      travellerType: this.detailsTravelerType,
      adults: this.detailsAdults,
      children: this.detailsChildren,
      minHotelStars: this.detailsMinHotelStars,
      maxHotelStars: this.detailsMaxHotelStars,
      notes: this.detailsNotes,
      currency: this.detailsCurrency,

      cabinClass: this.detailsCabinClass,
      directFlightsOnly: this.detailsDirectFlightsOnly,
      privateTransferPreferred: this.detailsPrivateTransfer,
      includeFoodTour: this.detailsIncludeFoodTour,
      activityIntensity: this.detailsActivityIntensity,
      nationality: this.detailsNationality,
      accessibilityRequired: this.detailsAccessibility,
      maxTravelTimePerDay: this.detailsMaxTravelTime,
      mustVisitPlaces: parseList(this.detailsMustVisit),
      avoidPlaces: parseList(this.detailsAvoid),
    };

    this.chatService.updateTripRequest(this.conversationId, updatePayload).subscribe({
      next: () => {
        this.showDetailsModal.set(false);
        this.loadMessages();
        this.loadWallpaper();
        this.chatService.conversationUpdated$.emit();
        this.notificationService.success('Travel preferences updated successfully!');
      },
      error: () => {
        this.notificationService.error('Failed to update travel preferences.');
      },
    });
  }

  onDetailsLocationChange() {
    if (!this.detailsBudget) {
      this.detailsBudget = 100000;
    }
  }

  stopStreaming() {
    if (this.activeStreamSubscription) {
      this.activeStreamSubscription.unsubscribe();
      this.activeStreamSubscription = undefined;
    }
    this.isStreaming.set(false);
    this.streamStatus.set(null);

    const msgs = this.messages();
    const lastMsg = msgs[msgs.length - 1];
    if (lastMsg && 'isStreaming' in lastMsg) {
      lastMsg.isStreaming = false;
    }
    this.messages.set([...msgs]);
  }

  sendMessage(event?: Event) {
    if (event) event.preventDefault();

    const content = this.inputMessage.trim();
    if (!content || !this.conversationId || this.isStreaming()) return;

    this.inputMessage = '';

    // Append user message immediately
    this.messages.update((msgs) => [
      ...msgs,
      { role: 'USER', content, timestamp: new Date().toISOString() },
    ]);
    this.shouldScrollToBottom = true;
    this.chatService.cacheMessages(this.conversationId!, this.messages());

    // Prepare assistant streaming buffer
    const assistantBuffer: AssistantBuffer = { role: 'ASSISTANT', content: '', isStreaming: true };
    this.messages.update((msgs) => [...msgs, assistantBuffer]);

    this.isStreaming.set(true);
    this.streamStatus.set('Connecting to AI...');

    this.activeStreamSubscription = this.chatService
      .getChatStream(this.conversationId!, content)
      .subscribe({
        next: ({ event, data }) => {
          if (event === 'status') {
            this.streamStatus.set(data);
          } else if (event === 'text') {
            assistantBuffer.content += data;
            this.messages.update((msgs) => [...msgs]);
            this.shouldScrollToBottom = true;
          }
        },
        error: () => {
          this.streamStatus.set(null);
          this.isStreaming.set(false);
          assistantBuffer.isStreaming = false;
          this.messages.update((msgs) => [...msgs]);
        },
        complete: () => {
          this.streamStatus.set(null);
          this.isStreaming.set(false);
          assistantBuffer.isStreaming = false;
          this.messages.update((msgs) => [...msgs]);
          this.shouldScrollToBottom = true;
          if (this.conversationId) {
            this.mapService.clearCache(this.conversationId);
          }
          this.chatService.conversationUpdated$.emit();
          this.loadMessages();
        },
      });
  }

  get allMessages() {
    return this.messages;
  }

  getMarkdownHtml(msg: ChatMessage | AssistantBuffer): SafeHtml {
    const rawMarkdown = msg.content || '';
    const parsedHtml = marked.parse(rawMarkdown, { async: false }) as string;
    return this.sanitizer.bypassSecurityTrustHtml(parsedHtml);
  }

  hasPdfDownload(msg: ChatMessage | AssistantBuffer): boolean {
    if (msg.role !== 'ASSISTANT') return false;
    return (msg.content || '').includes('[PDF_READY_DOWNLOAD]');
  }

  downloadPdf() {
    if (!this.conversationId) return;
    this.isExporting.set(true);
    this.chatService.getDownloadUrl(this.conversationId).subscribe({
      next: (res) => {
        this.isExporting.set(false);
        if (res?.downloadUrl) {
          window.open(res.downloadUrl, '_blank');
          this.notificationService.success('PDF itinerary download started!');
        } else {
          this.notificationService.error('Download URL not available.');
        }
      },
      error: () => {
        this.isExporting.set(false);
        this.notificationService.error('Failed to generate PDF download URL.');
      },
    });
  }

  deleteConversation() {
    if (!this.conversationId) return;
    this.openConfirm(
      'Delete Conversation',
      'Are you sure you want to delete this conversation? All itinerary plans, PDF documents, and shared public links will be permanently deleted.',
      () => {
        this.chatService.deleteConversation(this.conversationId!).subscribe({
          next: () => {
            const deletedId = this.conversationId;
            this.showHamburgerMenu.set(false);
            this.chatService.conversationUpdated$.emit();
            if (deletedId) {
              this.chatService.conversationDeleted$.emit(deletedId);
            }
            this.notificationService.success('Conversation and associated itinerary deleted.');
          },
          error: () => {
            this.notificationService.error('Failed to delete conversation.');
          },
        });
      },
    );
  }

  detailsShowOptional = false;

  generateItinerary() {
    this.sendMessage();
  }

  deleteActiveConversation() {
    this.deleteConversation();
  }

  downloadPdfFile(url?: string, dest?: string) {
    this.downloadPdf();
  }

  getPdfUrl(msg: any): string {
    return '';
  }

  getPdfDestination(msg: any): string {
    return '';
  }

  scrollToBottomSmooth() {
    this.scrollToBottom();
  }

  onFileSelected(event: any) {
    const file = event.target?.files?.[0];
    if (!file || !this.conversationId) return;
    this.isFileUploading.set(true);
    this.uploadProgressText.set('Ingesting document...');
    this.chatService.uploadPdf(this.conversationId, file).subscribe({
      next: () => {
        this.isFileUploading.set(false);
        this.notificationService.success('PDF document ingested successfully!');
        this.loadMessages();
      },
      error: () => {
        this.isFileUploading.set(false);
        this.notificationService.error('Failed to ingest PDF document.');
      },
    });
  }

  onDetailsHeadcountChange() {
    this.detailsAdults = Math.max(1, this.detailsHeadcount - this.detailsChildren);
  }

  onDetailsAdultsChange() {
    this.detailsHeadcount = this.detailsAdults + this.detailsChildren;
  }

  onDetailsChildrenChange() {
    this.detailsHeadcount = this.detailsAdults + this.detailsChildren;
  }

  openConfirm(title: string, message: string, action: () => void) {
    this.confirmTitle.set(title);
    this.confirmMessage.set(message);
    this.confirmAction = action;
    this.showConfirmModal.set(true);
  }

  confirmModalYes() {
    if (this.confirmAction) {
      this.confirmAction();
    }
    this.showConfirmModal.set(false);
  }

  confirmModalNo() {
    this.showConfirmModal.set(false);
  }

  onConfirmAccept() {
    this.confirmModalYes();
  }

  onConfirmCancel() {
    this.confirmModalNo();
  }
}
