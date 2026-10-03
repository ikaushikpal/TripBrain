import {
  Component,
  Input,
  Output,
  EventEmitter,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  inject,
  PLATFORM_ID,
  HostListener,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MapService } from '../../../core/services/map.service';

interface MapPreset {
  id: string;
  label: string;
  url: string;
  attribution: string;
  maxZoom: number;
}

@Component({
  selector: 'app-map-overlay',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './map-overlay.component.html',
})
export class MapOverlayComponent implements AfterViewInit, OnDestroy {
  @Input({ required: true }) conversationId!: string;
  @Output() closeMap = new EventEmitter<void>();
  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef;

  private platformId = inject(PLATFORM_ID);
  private mapService = inject(MapService);

  isLoading = true;
  errorMsg = false;
  private L: any;
  private mapInstance: any;
  private currentTileLayer: any;
  private routeLayerGroup: any;
  private geojsonData: any = null;

  mapStyles: MapPreset[] = [
    {
      id: 'osm',
      label: 'OpenStreetMap (Default)',
      url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    },
    {
      id: 'hot',
      label: 'Humanitarian OSM',
      url: 'https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png',
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, Tiles style by <a href="https://www.hotosm.org/">HOT</a>',
      maxZoom: 19,
    },
    {
      id: 'topo',
      label: 'OpenTopoMap',
      url: 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png',
      attribution: 'Map data: &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, SRTM | Map style: &copy; <a href="https://opentopomap.org">OpenTopoMap</a>',
      maxZoom: 17,
    },
    {
      id: 'satellite',
      label: 'ESRI Satellite',
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      attribution: 'Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and GIS Community',
      maxZoom: 18,
    },
    {
      id: 'street',
      label: 'ESRI Street Map',
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}',
      attribution: 'Tiles &copy; Esri &mdash; Source: Esri, DeLorme, NAVTEQ, USGS, Intermap, iPC, NRCAN, Esri Japan, METI, Esri China (Hong Kong), Esri (Thailand), TomTom, 2012',
      maxZoom: 18,
    },
  ];
  activeStyle = 'osm';

  // Dragging modal state
  isDraggingModal = false;
  translateX = 0;
  translateY = 0;
  private startX = 0;
  private startY = 0;

  ngAfterViewInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.initMap();
    }
  }

  onMouseDown(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (target.closest('button') || target.closest('#trip-map')) return;

    this.isDraggingModal = true;
    this.startX = event.clientX - this.translateX;
    this.startY = event.clientY - this.translateY;
  }

  @HostListener('window:mousemove', ['$event'])
  onMouseMove(event: MouseEvent) {
    if (!this.isDraggingModal) return;
    this.translateX = event.clientX - this.startX;
    this.translateY = event.clientY - this.startY;
  }

  @HostListener('window:mouseup')
  onMouseUp() {
    if (this.isDraggingModal) {
      this.isDraggingModal = false;
      if (this.mapInstance) {
        this.mapInstance.invalidateSize();
      }
    }
  }

  setMapStyle(styleId: string) {
    if (this.activeStyle === styleId || !this.mapInstance || !this.L) return;
    this.activeStyle = styleId;
    const selected = this.mapStyles.find((s) => s.id === styleId);
    if (selected) {
      if (this.currentTileLayer) {
        this.mapInstance.removeLayer(this.currentTileLayer);
      }
      this.currentTileLayer = this.L.tileLayer(selected.url, {
        attribution: selected.attribution,
        maxZoom: selected.maxZoom,
      }).addTo(this.mapInstance);
    }
  }

  private async initMap() {
    const leafletModule = await import('leaflet');
    this.L = leafletModule.default || leafletModule;

    const initialPreset = this.mapStyles.find((s) => s.id === this.activeStyle) || this.mapStyles[0];

    this.mapInstance = this.L.map(this.mapContainer.nativeElement, {
      center: [20, 0],
      zoom: 3,
      zoomControl: true,
      dragging: true,
      scrollWheelZoom: true,
      touchZoom: true,
      doubleClickZoom: true,
      boxZoom: true,
    });

    this.currentTileLayer = this.L.tileLayer(initialPreset.url, {
      attribution: initialPreset.attribution,
      maxZoom: initialPreset.maxZoom,
    }).addTo(this.mapInstance);

    this.routeLayerGroup = this.L.featureGroup().addTo(this.mapInstance);

    // Multiple invalidateSize ticks to ensure correct sizing and drag handlers
    setTimeout(() => {
      if (this.mapInstance) {
        this.mapInstance.invalidateSize();
      }
    }, 100);

    setTimeout(() => {
      if (this.mapInstance) {
        this.mapInstance.invalidateSize();
      }
    }, 400);

    this.mapService.getMapRoute(this.conversationId).subscribe({
      next: (geojson) => {
        this.isLoading = false;
        if (!geojson?.features?.length) {
          this.errorMsg = true;
          return;
        }

        this.geojsonData = geojson;
        this.renderRouteAndPins();
      },
      error: () => {
        this.isLoading = false;
        this.errorMsg = true;
      },
    });
  }

  private renderRouteAndPins() {
    if (!this.mapInstance || !this.routeLayerGroup || !this.geojsonData?.features?.length || !this.L) {
      return;
    }

    this.routeLayerGroup.clearLayers();

    const pointFeatures = this.geojsonData.features.filter(
      (f: any) => f.geometry?.type === 'Point',
    );
    const lineFeatures = this.geojsonData.features.filter(
      (f: any) => f.geometry?.type === 'LineString',
    );

    // 1. Render glowing route lines
    lineFeatures.forEach((feature: any) => {
      const coords = feature.geometry.coordinates; // [[lon, lat], ...]
      const latlngs = coords.map((c: [number, number]) => [c[1], c[0]]);

      // Outer glow line
      this.L.polyline(latlngs, {
        color: '#0284c7',
        weight: 8,
        opacity: 0.45,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(this.routeLayerGroup);

      // Core dashed sky blue line
      this.L.polyline(latlngs, {
        color: '#38bdf8',
        weight: 4,
        opacity: 0.95,
        dashArray: '8, 6',
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(this.routeLayerGroup);
    });

    // 2. Render Pins for Departure, Must-Visit Places, Destination
    let waypointIndex = 1;

    pointFeatures.forEach((feature: any) => {
      const type = feature.properties?.type;
      const isStart = type === 'START';
      const isEnd = type === 'END';
      const isWaypoint = type === 'WAYPOINT';

      const [lon, lat] = feature.geometry.coordinates;
      const latlng = [lat, lon];

      let bgColor = '#f59e0b';
      let iconOrLabel = '📍';
      let badgeCategory = feature.properties?.category || 'Attraction';

      if (isStart) {
        bgColor = '#10b981';
        iconOrLabel = '🛫';
        badgeCategory = 'Departure';
      } else if (isEnd) {
        bgColor = '#ef4444';
        iconOrLabel = '🛬';
        badgeCategory = 'Destination';
      } else if (isWaypoint) {
        bgColor = '#f59e0b';
        iconOrLabel = `${waypointIndex++}`;
        badgeCategory = 'Must-Visit Place';
      }

      const pinHtml = `
        <div class="custom-map-pin" style="
          width: 38px;
          height: 38px;
          border-radius: 50%;
          background-color: ${bgColor};
          border: 3px solid #ffffff;
          box-shadow: 0 4px 14px rgba(0, 0, 0, 0.6);
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 700;
          color: #ffffff;
          font-size: ${isWaypoint ? '14px' : '17px'};
          line-height: 1;
        ">${iconOrLabel}</div>
      `;

      const customIcon = this.L.divIcon({
        className: 'custom-leaflet-div-icon',
        html: pinHtml,
        iconSize: [38, 38],
        iconAnchor: [19, 19],
        popupAnchor: [0, -22],
      });

      const popupHtml = `
        <div style="font-family: 'Outfit', sans-serif; min-width: 140px;">
          <div style="font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 700; color: ${bgColor}; margin-bottom: 2px;">
            ${badgeCategory}
          </div>
          <strong style="color: #ffffff; font-size: 14px; display: block; line-height: 1.3;">
            ${feature.properties?.title ?? ''}
          </strong>
        </div>
      `;

      this.L.marker(latlng, { icon: customIcon, zIndexOffset: isStart || isEnd ? 1000 : 500 })
        .bindPopup(popupHtml)
        .addTo(this.routeLayerGroup);
    });

    // 3. Smooth bounds fitting
    const bounds = this.routeLayerGroup.getBounds();
    if (bounds.isValid()) {
      this.mapInstance.fitBounds(bounds, { padding: [50, 50], maxZoom: 13 });
    }

    setTimeout(() => {
      if (this.mapInstance) {
        this.mapInstance.invalidateSize();
      }
    }, 250);
  }

  ngOnDestroy() {
    this.mapInstance?.remove();
  }
}
