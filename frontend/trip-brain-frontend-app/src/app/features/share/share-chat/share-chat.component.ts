import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ChatService, ChatMessage, Conversation } from '../../../core/services/chat.service';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';

@Component({
  selector: 'app-share-chat',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './share-chat.component.html',
  styleUrls: ['./share-chat.component.css']
})
export class ShareChatComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly chatService = inject(ChatService);
  private readonly sanitizer = inject(DomSanitizer);

  conversationId = signal<string | null>(null);
  conversation = signal<Conversation | null>(null);
  messages = signal<any[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);
  currentYear = new Date().getFullYear();

  wallpaperUrl = signal<string | null>(null);

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.conversationId.set(id);
        this.loadSharedChat(id);
        this.loadWallpaper(id);
      } else {
        this.error.set("Invalid conversation link.");
        this.isLoading.set(false);
      }
    });
  }

  loadSharedChat(id: string) {
    this.isLoading.set(true);
    this.error.set(null);
    this.chatService.getSharedConversationMessages(id).subscribe({
      next: (data) => {
        this.conversation.set(data.conversation);
        this.messages.set(data.messages || []);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.error.set("Failed to load conversation. Make sure it is public and the link is correct.");
        this.isLoading.set(false);
      }
    });
  }

  loadWallpaper(id: string) {
    this.chatService.getDestinationImage(id).subscribe({
      next: (res) => {
        this.wallpaperUrl.set(res.imageUrl);
      },
      error: () => {
        this.wallpaperUrl.set(null);
      }
    });
  }

  hasPdfDownload(msg: any): boolean {
    if (msg.messageType === 'PDF_DOWNLOAD') return true;
    return !!(msg.content && msg.content.includes('[PDF_DOWNLOAD_METADATA:'));
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

  downloadPdfFile(url: string) {
    const convId = this.conversationId();
    if (!convId) {
      if (url) {
        window.open(`http://localhost:8080${url}`, '_blank');
      }
      return;
    }
    this.chatService.getDownloadUrl(convId).subscribe({
      next: (res) => {
        console.log("Frontend Direct B2 Download URL (Shared):", res.downloadUrl);
        window.open(res.downloadUrl, '_blank');
      },
      error: (err) => {
        console.error("Failed to fetch direct download url, falling back to redirect:", err);
        const downloadUrl = `http://localhost:8080${url}`;
        window.open(downloadUrl, '_blank');
      }
    });
  }
}
