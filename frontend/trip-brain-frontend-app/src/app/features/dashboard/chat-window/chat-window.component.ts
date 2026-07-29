import { Component, signal, Input, OnChanges, SimpleChanges, inject, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { ChatService, ChatMessage } from '../../../core/services/chat.service';
import { NotificationService } from '../../../core/services/notification.service';
import { MapOverlayComponent } from '../../../shared/components/map-overlay/map-overlay.component';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';

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
  styleUrl: './chat-window.component.css'
})
export class ChatWindowComponent implements OnChanges, AfterViewChecked {
  @Input() conversationId: string | null = null;
  @ViewChild('messagesContainer') messagesContainer!: ElementRef;

  private readonly chatService = inject(ChatService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);

  messages = signal<(ChatMessage | AssistantBuffer)[]>([]);
  streamStatus = signal<string | null>(null);
  isStreaming = signal(false);
  isExporting = signal(false);
  showMap = signal(false);
  inputMessage = '';
  private shouldScrollToBottom = false;

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
  detailsShowOptional = false;
  detailsCurrency = 'INR';

  nextCursor: number | null = null;
  isLoadingMore = signal(false);

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

  get allMessages() {
    return this.messages;
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['conversationId']) {
      const current = changes['conversationId'].currentValue;
      if (current) {
        this.loadMessages();
        this.loadWallpaper();
      } else {
        this.messages.set([]);
        this.wallpaperUrl.set(null);
        this.currentConversation.set(null);
        this.inputMessage = '';
      }
    }
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  private scrollToBottom() {
    try {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    } catch (e) {}
  }

  onScroll(event: Event) {
    const el = event.target as HTMLElement;
    if (el.scrollTop < 50 && this.nextCursor !== null && !this.isLoadingMore()) {
      this.loadMoreMessages();
    }
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
      }
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
      error: () => {}
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
      error: () => {}
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
      }
    });
  }

  togglePublicState() {
    const conv = this.currentConversation();
    if (!conv) return;
    const currentState = !!conv.isPublic;
    const nextState = !currentState;

    if (nextState) {
      this.openConfirm(
        "Make Conversation Public",
        "Are you sure you want to make this conversation public? Anyone with the shareable link will be able to view its contents.",
        () => this.executeTogglePublic(conv.id, nextState)
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
        this.notificationService.success(`Conversation visibility updated to: ${nextState ? 'Public' : 'Private'}`);
      },
      error: () => {
        this.notificationService.error("Failed to update conversation visibility.");
      }
    });
  }

  shareConversationLink() {
    const conv = this.currentConversation();
    if (!conv) return;

    if (!conv.isPublic) {
      this.openConfirm(
        "Make Conversation Public",
        "This conversation must be public in order to share it. Make it public now?",
        () => {
          this.chatService.togglePublic(conv.id, true).subscribe({
            next: () => {
              conv.isPublic = true;
              this.currentConversation.set({ ...conv });
              this.copyShareLink(conv.id);
              this.chatService.conversationUpdated$.emit();
            },
            error: () => {
              this.notificationService.error("Failed to make conversation public.");
            }
          });
        }
      );
    } else {
      this.copyShareLink(conv.id);
    }
  }

  private copyShareLink(conversationId: string) {
    const origin = typeof window !== 'undefined' ? window.location.origin : '';
    const shareUrl = `${origin}/share/${conversationId}`;
    navigator.clipboard.writeText(shareUrl).then(() => {
      this.notificationService.success("Read-only share link copied to clipboard!");
      this.showHamburgerMenu.set(false);
    }).catch(err => {
      console.error("Failed to copy link", err);
      this.notificationService.info(`Here is your share link: ${shareUrl}`);
    });
  }

  deleteActiveConversation() {
    const conv = this.currentConversation();
    if (!conv) return;

    this.openConfirm(
      "Delete Conversation",
      "Are you sure you want to delete this conversation?",
      () => {
        this.chatService.deleteConversation(conv.id).subscribe({
          next: () => {
            this.showHamburgerMenu.set(false);
            this.chatService.conversationUpdated$.emit();
            this.conversationId = null;
            this.currentConversation.set(null);
            this.messages.set([]);
          },
          error: () => {
            this.notificationService.error("Failed to delete conversation.");
          }
        });
      }
    );
  }

  openDetailsModal() {
    if (!this.conversationId) return;
    this.chatService.getTripRequest(this.conversationId).subscribe({
      next: (req) => {
        this.detailsSource = req.source || '';
        this.detailsDestination = req.destination || '';
        this.detailsStartDate = req.startDate || '';
        this.detailsEndDate = req.endDate || '';
        this.detailsBudget = req.maxBudgetInr;
        this.detailsHeadcount = req.headcount || 2;
        this.detailsBudgetClass = req.budgetPreference || 'MID';
        this.detailsTravelerType = req.travellerType || 'COUPLE';
        this.detailsAdults = req.adults || 2;
        this.detailsChildren = req.children || 0;
        this.detailsMinHotelStars = req.minHotelStars || 3;
        this.detailsMaxHotelStars = req.maxHotelStars || 5;
        this.detailsNotes = req.notes || '';
        this.detailsCurrency = req.currency || 'INR';
        
        this.showDetailsModal.set(true);
      },
      error: () => {
        this.notificationService.info("No travel preferences are configured for this chat.");
      }
    });
  }

  saveTripDetails(event: Event) {
    event.preventDefault();
    if (!this.conversationId) return;

    if (!this.detailsSource || !this.detailsDestination || !this.detailsStartDate || !this.detailsEndDate) {
      this.notificationService.error("Source, Destination, Start Date and End Date are required.");
      return;
    }
    if (new Date(this.detailsEndDate) < new Date(this.detailsStartDate)) {
      this.notificationService.error("End date must be on or after the start date.");
      return;
    }

    const payload = {
      source: this.detailsSource,
      destination: this.detailsDestination,
      startDate: this.detailsStartDate,
      endDate: this.detailsEndDate,
      maxBudgetInr: this.detailsBudget,
      headcount: this.detailsHeadcount,
      budgetPreference: this.detailsBudgetClass,
      travellerType: this.detailsTravelerType,
      adults: this.detailsAdults,
      children: this.detailsChildren,
      minHotelStars: this.detailsMinHotelStars,
      maxHotelStars: this.detailsMaxHotelStars,
      notes: this.detailsNotes,
      currency: this.detailsCurrency
    };

    this.chatService.updateTripRequest(this.conversationId, payload).subscribe({
      next: () => {
        this.showDetailsModal.set(false);
        this.loadMessages();
        this.loadWallpaper();
        this.chatService.conversationUpdated$.emit();
        this.notificationService.success("Travel preferences updated successfully!");
      },
      error: () => {
        this.notificationService.error("Failed to update travel preferences.");
      }
    });
  }

  onDetailsLocationChange() {
    this.detailsBudget = null;
    this.detailsHeadcount = 1;
    this.detailsAdults = 1;
    this.detailsChildren = 0;
    this.detailsMinHotelStars = 3;
    this.detailsMaxHotelStars = 5;
    this.detailsNotes = '';
  }
  sendMessage(event?: Event) {
    if (event) event.preventDefault();

    const content = this.inputMessage.trim();
    if (!content || !this.conversationId || this.isStreaming()) return;

    this.inputMessage = '';

    // Append user message immediately
    this.messages.update(msgs => [...msgs, { role: 'USER', content, timestamp: new Date().toISOString() }]);
    this.shouldScrollToBottom = true;
    this.chatService.cacheMessages(this.conversationId!, this.messages());

    // Prepare assistant streaming buffer
    const assistantBuffer: AssistantBuffer = { role: 'ASSISTANT', content: '', isStreaming: true };
    this.messages.update(msgs => [...msgs, assistantBuffer]);

    this.isStreaming.set(true);
    this.streamStatus.set('Connecting to AI...');

    this.chatService.getChatStream(this.conversationId!, content).subscribe({
      next: ({ event, data }) => {
        if (event === 'status') {
          this.streamStatus.set(data);
        } else if (event === 'text') {
          assistantBuffer.content += data;
          this.messages.update(msgs => [...msgs]);
          this.shouldScrollToBottom = true;
        }
      },
      error: () => {
        this.streamStatus.set(null);
        this.isStreaming.set(false);
        assistantBuffer.isStreaming = false;
        this.messages.update(msgs => [...msgs]);
      },
      complete: () => {
        this.streamStatus.set(null);
        this.isStreaming.set(false);
        assistantBuffer.isStreaming = false;
        this.messages.update(msgs => [...msgs]);
        this.shouldScrollToBottom = true;
        this.chatService.conversationUpdated$.emit();
        this.loadMessages();
      }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0 || !this.conversationId) return;

    const file = input.files[0];
    this.isFileUploading.set(true);
    this.uploadError.set(null);
    this.uploadProgressText.set('Obtaining upload link...');

    // 1. Fetch Presigned URL from Backend
    this.chatService.getPresignedUploadUrl(this.conversationId, file.name, file.type).subscribe({
      next: (res) => {
        this.uploadProgressText.set('Uploading directly to Backblaze...');
        // 2. Upload file directly to Backblaze B2 S3 bucket
        this.chatService.uploadToPresignedUrl(res.uploadUrl, file).subscribe({
          next: () => {
            this.uploadProgressText.set('Running document parsing & OCR extraction...');
            // 3. Request backend to run parsing, OCR (Tess4J), check relevance and index context
            this.chatService.processUpload(this.conversationId!, res.fileKey, file.type, file.name).subscribe({
              next: (processRes) => {
                this.isFileUploading.set(false);
                if (processRes.status === 'REJECTED') {
                  this.uploadError.set(processRes.message);
                } else {
                  this.loadMessages(); // Refresh messages to show the extracted document turn
                }
              },
              error: (err) => {
                this.isFileUploading.set(false);
                this.uploadError.set(err?.error?.message || 'Failed to process document content.');
              }
            });
          },
          error: () => {
            this.isFileUploading.set(false);
            this.uploadError.set('Failed to upload file to storage.');
          }
        });
      },
      error: () => {
        this.isFileUploading.set(false);
        this.uploadError.set('Failed to authorize file upload.');
      }
    });
  }

  generateItinerary() {
    this.inputMessage = "Generate Itinerary";
    this.sendMessage();
  }

  hasPdfDownload(msg: any): boolean {
    if (msg.messageType === 'PDF_DOWNLOAD') return true;
    return msg.content && msg.content.includes('[PDF_DOWNLOAD_METADATA:');
  }

  getPdfUrl(msg: any): string {
    if (msg.messageType === 'PDF_DOWNLOAD') {
      try {
        const meta = JSON.parse(msg.metadataJson);
        return meta.url;
      } catch (e) {
        return '';
      }
    }
    if (msg.content && msg.content.includes('[PDF_DOWNLOAD_METADATA:')) {
      try {
        const start = msg.content.indexOf('[PDF_DOWNLOAD_METADATA:');
        const end = msg.content.indexOf(']', start);
        const jsonStr = msg.content.substring(start + '[PDF_DOWNLOAD_METADATA:'.length, end);
        const meta = JSON.parse(jsonStr);
        return meta.url;
      } catch (e) {
        return '';
      }
    }
    return '';
  }

  getPdfDestination(msg: any): string {
    if (msg.messageType === 'PDF_DOWNLOAD') {
      try {
        const meta = JSON.parse(msg.metadataJson);
        return meta.destination || 'Trip';
      } catch (e) {
        return 'Trip';
      }
    }
    if (msg.content && msg.content.includes('[PDF_DOWNLOAD_METADATA:')) {
      try {
        const start = msg.content.indexOf('[PDF_DOWNLOAD_METADATA:');
        const end = msg.content.indexOf(']', start);
        const jsonStr = msg.content.substring(start + '[PDF_DOWNLOAD_METADATA:'.length, end);
        const meta = JSON.parse(jsonStr);
        return meta.destination || 'Trip';
      } catch (e) {
        return 'Trip';
      }
    }
    return 'Trip';
  }

  getCleanContent(msg: any): string {
    let content = msg.content || '';
    if (content.includes('[PDF_DOWNLOAD_METADATA:')) {
      const start = content.indexOf('[PDF_DOWNLOAD_METADATA:');
      const end = content.indexOf(']', start);
      content = content.replace(content.substring(start, end + 1), '').trim();
    }
    return content;
  }

  getMarkdownHtml(msg: any): SafeHtml {
    const clean = this.getCleanContent(msg);
    if (!clean) return '';
    try {
      let rawHtml = marked.parse(clean) as string;
      rawHtml = rawHtml.replace(/href="([^"]+)"/g, (match, url) => {
        let processedUrl = url;
        if (url.includes('google.com/maps/')) {
          let query = '';
          if (url.includes('/place/')) {
            const parts = url.split('/place/');
            query = parts[1] ? parts[1].split('/')[0] : '';
          } else if (url.includes('/search/')) {
            const parts = url.split('/search/');
            if (parts[1]) {
              if (parts[1].startsWith('?')) {
                try {
                  const urlObj = new URL(url);
                  query = urlObj.searchParams.get('query') || '';
                } catch {
                  const queryPart = parts[1].split('query=')[1];
                  query = queryPart ? queryPart.split('&')[0] : '';
                }
              } else {
                query = parts[1].split('/')[0];
              }
            }
          }
          if (query) {
            query = query.replace(/[)/]+$/, '');
            processedUrl = `https://www.google.com/maps/search/?api=1&query=${query}`;
          }
        }
        return `href="${processedUrl}" target="_blank" rel="noopener noreferrer"`;
      });
      return this.sanitizer.bypassSecurityTrustHtml(rawHtml);
    } catch (e) {
      return clean;
    }
  }

  downloadPdfFile(url: string, destination: string) {
    if (!this.conversationId) {
      if (url) {
        window.open(`http://localhost:8080${url}`, '_blank');
      }
      return;
    }
    this.chatService.getDownloadUrl(this.conversationId).subscribe({
      next: (res) => {
        console.log("Frontend Direct B2 Download URL:", res.downloadUrl);
        window.open(res.downloadUrl, '_blank');
      },
      error: (err) => {
        console.error("Failed to fetch direct download url, falling back to redirect:", err);
        const token = this.authService.getAccessToken();
        const downloadUrl = `http://localhost:8080${url}` + (token ? `?token=${encodeURIComponent(token)}` : '');
        window.open(downloadUrl, '_blank');
      }
    });
  }
}
