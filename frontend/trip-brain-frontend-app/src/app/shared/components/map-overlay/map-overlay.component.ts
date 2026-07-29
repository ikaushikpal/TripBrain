import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, ElementRef, ViewChild, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MapService } from '../../../core/services/map.service';

@Component({
  selector: 'app-map-overlay',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './map-overlay.component.html'
})
export class MapOverlayComponent implements OnInit, OnDestroy {
  @Input({ required: true }) conversationId!: string;
  @Output() closeMap = new EventEmitter<void>();
  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef;

  private platformId = inject(PLATFORM_ID);
  private mapService = inject(MapService);

  isLoading = true;
  errorMsg = false;
  private mapInstance: any;

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.initMap();
    }
  }

  private async initMap() {
    const L = await import('leaflet');

    this.mapInstance = L.map(this.mapContainer.nativeElement, {
      zoom: 5,
      center: [20, 0],
      zoomControl: true
    });

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: '© OpenStreetMap contributors, © CARTO',
      maxZoom: 19
    }).addTo(this.mapInstance);

    this.mapService.getMapRoute(this.conversationId).subscribe({
      next: (geojson) => {
        this.isLoading = false;
        if (!geojson?.features?.length) {
          this.errorMsg = true;
          return;
        }

        const layer = L.geoJSON(geojson, {
          style: (feature: any) => {
            if (feature?.geometry.type === 'LineString') {
              return { color: '#3b82f6', weight: 3, opacity: 0.9, dashArray: '6,4' };
            }
            return {};
          },
          pointToLayer: (feature: any, latlng: any) => {
            const isStart = feature.properties?.type === 'START';
            const icon = L.divIcon({
              className: '',
              html: `<div style="
                width:36px;height:36px;
                background:${isStart ? '#22c55e' : '#ef4444'};
                border:3px solid white;
                border-radius:50%;
                box-shadow:0 4px 12px rgba(0,0,0,0.4);
                display:flex;align-items:center;justify-content:center;
                font-size:16px;
              ">${isStart ? '🛫' : '🛬'}</div>`,
              iconSize: [36, 36],
              iconAnchor: [18, 18]
            });
            return L.marker(latlng, { icon }).bindPopup(
              `<b>${feature.properties?.title ?? ''}</b><br><small>${isStart ? 'Departure' : 'Destination'}</small>`
            );
          }
        }).addTo(this.mapInstance);

        const bounds = layer.getBounds();
        if (bounds.isValid()) {
          this.mapInstance.fitBounds(bounds, { padding: [40, 40] });
        }
      },
      error: () => {
        this.isLoading = false;
        this.errorMsg = true;
      }
    });
  }

  ngOnDestroy() {
    this.mapInstance?.remove();
  }
}
