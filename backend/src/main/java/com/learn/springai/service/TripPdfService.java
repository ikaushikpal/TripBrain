package com.learn.springai.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.io.InputStream;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.layout.font.FontProvider;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.learn.springai.dto.trip.ActivityDTO;
import com.learn.springai.dto.trip.CostBreakdownDTO;
import com.learn.springai.dto.trip.DayCostSummaryDTO;
import com.learn.springai.dto.trip.DayDTO;
import com.learn.springai.dto.trip.HotelDTO;
import com.learn.springai.dto.trip.MetaDTO;
import com.learn.springai.dto.trip.RouteStopDTO;
import com.learn.springai.dto.trip.TransportLegDTO;
import com.learn.springai.dto.trip.TripPlanDTO;
import com.learn.springai.dto.trip.ValidationDTO;
import com.learn.springai.dto.trip.WeatherDTO;
import com.learn.springai.model.Conversation;
import com.learn.springai.model.TripPdf;
import com.learn.springai.model.TripRequest;
import com.learn.springai.repository.ConversationRepository;
import com.learn.springai.repository.TripPdfRepository;
import com.learn.springai.repository.TripRequestRepository;
import com.learn.springai.config.DatabaseViewManager;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripPdfService {
    private String uploadDir = "uploads";

    private final TripPdfRepository tripPdfRepository;
    private final ConversationRepository conversationRepository;
    private final TripRequestRepository tripRequestRepository;
    private final DatabaseViewManager databaseViewManager;
    private final BackblazeStorageService backblazeStorageService;
    private final UnsplashService unsplashService;
    private final MarkdownToPdfRenderer markdownToPdfRenderer;

    @PostConstruct
    public void init() {
        log.info("UPLOAD DIR = {}", uploadDir);
    }

    // ─── Colours ────────────────────────────────────────────────────────────
    private static final DeviceRgb PRIMARY    = new DeviceRgb(0x1A, 0x73, 0xE8);
    private static final DeviceRgb ACCENT     = new DeviceRgb(0xFF, 0x6F, 0x00);
    private static final DeviceRgb BG_LIGHT   = new DeviceRgb(0xF8, 0xF9, 0xFA);
    private static final DeviceRgb TEXT_DARK  = new DeviceRgb(0x21, 0x21, 0x21);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(0x75, 0x75, 0x75);
    private static final DeviceRgb DIVIDER    = new DeviceRgb(0xE0, 0xE0, 0xE0);
    private static final DeviceRgb WHITE      = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb DAY_HEADER = new DeviceRgb(0xE8, 0xF0, 0xFE);
    private static final DeviceRgb WARNING_BG   = new DeviceRgb(0xFF, 0xF3, 0xE0);
    private static final DeviceRgb WARNING_TEXT  = new DeviceRgb(0xE6, 0x51, 0x00);

    // ─── Fonts ───────────────────────────────────────────────────────────────
    private PdfFont fontRegular;
    private PdfFont fontBold;
    private PdfFont fontItalic;

    // ────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ────────────────────────────────────────────────────────────────────────

    @Transactional
    public TripPdf generateAndSave(String conversationId,
            TripPlanDTO plan,
            boolean isPublic) throws IOException {

        log.info("[TripPdfService] Starting PDF generation from DTO for conversation ID: {}", conversationId);
        Conversation conversation = conversationRepository.getById(conversationId);
        String creatorName = (conversation != null && conversation.getUser() != null) ? conversation.getUser().getName() : "Traveler";

        // 1. Generate PDF bytes
        byte[] pdfBytes = buildPdf(plan, conversationId.toString(), creatorName);

        // 2. Upload PDF directly to Backblaze B2 under /final_trip_pdfs/
        String s3Key = "final_trip_pdfs/" + conversationId + ".pdf";
        String filePath = s3Key;
        backblazeStorageService.uploadFile(s3Key, pdfBytes, "application/pdf");
        log.info("[TripPdfService] DTO PDF compiled and successfully uploaded to Backblaze B2: {}", s3Key);

        // 3. Upsert metadata
        TripPdf entity = tripPdfRepository
                .findByConversationId(conversationId)
                .orElse(TripPdf.builder()
                        .conversation(conversation)
                        .build());

        entity.setFilePath(filePath);
        entity.setPublicUrl("/api/conversations/trips/" + conversationId + "/download");
        entity.setPublic(isPublic);
        entity.setGeneratedAt(LocalDateTime.now());
        entity.setDestination(plan.getMeta().getDestination());
        entity.setTags(generateTags(conversation, "", plan.getMeta().getDestination()));

        // Generate dynamic vector SVG thumbnail preview for frontend cards
        try {
            String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now());
            String dest = plan.getMeta().getDestination();
            String svgContent = String.format("""
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 250" width="100%%" height="100%%">
              <defs>
                <linearGradient id="grad" x1="0%%" y1="0%%" x2="100%%" y2="100%%">
                  <stop offset="0%%" style="stop-color:#1A73E8;stop-opacity:1" />
                  <stop offset="100%%" style="stop-color:#FF6F00;stop-opacity:1" />
                </linearGradient>
              </defs>
              <rect width="100%%" height="100%%" fill="url(#grad)" rx="15" />
              <text x="50%%" y="45%%" font-family="Arial, sans-serif" font-size="24" font-weight="bold" fill="#ffffff" text-anchor="middle">%s</text>
              <text x="50%%" y="65%%" font-family="Arial, sans-serif" font-size="14" fill="#ffffff" opacity="0.9" text-anchor="middle">Custom Travel Itinerary</text>
              <text x="50%%" y="85%%" font-family="Arial, sans-serif" font-size="11" fill="#ffffff" opacity="0.7" text-anchor="middle">Generated: %s</text>
            </svg>
            """, dest, dateStr);
            
            String svgKey = "final_trip_pdfs/" + conversationId + "-thumbnail.svg";
            backblazeStorageService.uploadFile(svgKey, svgContent.getBytes(java.nio.charset.StandardCharsets.UTF_8), "image/svg+xml");
            entity.setThumbnailUrl("/api/conversations/trips/" + conversationId + "/thumbnail");
        } catch (Exception e) {
            log.warn("Failed to generate PDF thumbnail preview SVG: {}", e.getMessage());
        }

        TripPdf savedEntity = tripPdfRepository.save(entity);
        log.info("[TripPdfService] PDF metadata record successfully stored in database with ID: {} for conversation ID: {}", savedEntity.getId(), conversationId);
        databaseViewManager.refreshViewAsync();
        return savedEntity;
    }

    /**
     * Markdown-first path: converts a structured Markdown string to PDF
     * deterministically via MarkdownToPdfRenderer, bypassing all JSON/DTO deserialization.
     */
    @Transactional
    public TripPdf generateAndSaveFromMarkdown(String conversationId,
                                               String markdown,
                                               String destination,
                                               boolean isPublic) throws IOException {
        log.info("[TripPdfService] Starting PDF generation from markdown for conversation ID: {}", conversationId);
        Conversation conversation = conversationRepository.getById(conversationId);
        String creatorName = (conversation != null && conversation.getUser() != null) ? conversation.getUser().getName() : "Traveler";

        byte[] pdfBytes = markdownToPdfRenderer.render(markdown, creatorName);

        String s3Key = "final_trip_pdfs/" + conversationId + ".pdf";
        String filePath = s3Key;
        backblazeStorageService.uploadFile(s3Key, pdfBytes, "application/pdf");
        log.info("[TripPdfService] Markdown PDF compiled and successfully uploaded to Backblaze B2: {}", s3Key);

        TripPdf entity = tripPdfRepository
                .findByConversationId(conversationId)
                .orElse(TripPdf.builder().conversation(conversation).build());

        entity.setFilePath(filePath);
        entity.setPublicUrl("/api/conversations/trips/" + conversationId + "/download");
        entity.setPublic(isPublic);
        entity.setGeneratedAt(LocalDateTime.now());
        entity.setDestination(destination);
        entity.setTags(generateTags(conversation, markdown, destination));

        // SVG thumbnail
        try {
            String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .format(LocalDateTime.now());
            String svgContent = String.format("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 250" width="100%%" height="100%%">
                  <defs>
                    <linearGradient id="grad" x1="0%%" y1="0%%" x2="100%%" y2="100%%">
                      <stop offset="0%%" style="stop-color:#1A73E8;stop-opacity:1" />
                      <stop offset="100%%" style="stop-color:#FF6F00;stop-opacity:1" />
                    </linearGradient>
                  </defs>
                  <rect width="100%%" height="100%%" fill="url(#grad)" rx="15" />
                  <text x="50%%" y="45%%" font-family="Arial, sans-serif" font-size="24" font-weight="bold" fill="#ffffff" text-anchor="middle">%s</text>
                  <text x="50%%" y="65%%" font-family="Arial, sans-serif" font-size="14" fill="#ffffff" opacity="0.9" text-anchor="middle">Custom Travel Itinerary</text>
                  <text x="50%%" y="85%%" font-family="Arial, sans-serif" font-size="11" fill="#ffffff" opacity="0.7" text-anchor="middle">Generated: %s</text>
                </svg>
                """, destination, dateStr);
            String svgKey = "final_trip_pdfs/" + conversationId + "-thumbnail.svg";
            backblazeStorageService.uploadFile(svgKey,
                    svgContent.getBytes(java.nio.charset.StandardCharsets.UTF_8), "image/svg+xml");
            entity.setThumbnailUrl("/api/conversations/trips/" + conversationId + "/thumbnail");
        } catch (Exception e) {
            log.warn("Failed to generate SVG thumbnail: {}", e.getMessage());
        }

        TripPdf saved = tripPdfRepository.save(entity);
        log.info("[TripPdfService] PDF metadata record successfully stored in database with ID: {} for conversation ID: {}", saved.getId(), conversationId);
        databaseViewManager.refreshViewAsync();
        return saved;
    }

    /**
     * Reads a previously generated PDF from disk.
     * Throws 404 if not found or not accessible.
     */
    public byte[] read(String conversationId, boolean requestedByOwner) throws IOException {
        TripPdf meta = tripPdfRepository
                .findByConversationId(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "PDF not found"));

        if (!meta.isPublic() && !requestedByOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This trip plan is private");
        }

        Path path = Paths.get(meta.getFilePath());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "PDF file missing from disk");
        }

        return Files.readAllBytes(path);
    }

    public List<TripPdf> listPublic() {
        return tripPdfRepository.findAllByIsPublicTrueOrderByGeneratedAtDesc();
    }

    // ────────────────────────────────────────────────────────────────────────
    // PDF BUILDER
    // ────────────────────────────────────────────────────────────────────────

    private byte[] buildPdf(TripPlanDTO plan, String conversationId, String creatorName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(40, 40, 50, 40);

        initFonts();
        setupFontProvider(document);
        addPageNumbers(pdfDoc);

        // ── Cover ────────────────────────────────────────────────────────────
        addCover(document, plan.getMeta(), conversationId, creatorName);
        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

        // ── Route Overview ───────────────────────────────────────────────────
        addSectionHeading(document, "Route Overview");
        addRouteTable(document, plan.getRouteOverview());
        document.add(new Paragraph("\n"));

        // ── Cost Breakdown ───────────────────────────────────────────────────
        if (plan.getCostBreakdown() != null) {
            addSectionHeading(document, "Cost Breakdown");
            addCostTable(document, plan.getCostBreakdown(), plan.getMeta());
            document.add(new Paragraph("\n"));
        }

        // ── Hotels ───────────────────────────────────────────────────────────
        if (plan.getHotels() != null && !plan.getHotels().isEmpty()) {
            addSectionHeading(document, "Accommodation");
            addHotelsTable(document, plan.getHotels());
            document.add(new Paragraph("\n"));
        }

        // ── Transport Legs ───────────────────────────────────────────────────
        if (plan.getTransportLegs() != null && !plan.getTransportLegs().isEmpty()) {
            addSectionHeading(document, "Transport");
            addTransportTable(document, plan.getTransportLegs());
            document.add(new Paragraph("\n"));
        }

        // ── Day-by-Day ───────────────────────────────────────────────────────
        addSectionHeading(document, "Day-by-Day Itinerary");
        for (DayDTO day : plan.getDays()) {
            addDayCard(document, day);
        }

        // ── Warnings ─────────────────────────────────────────────────────────
        if (plan.getValidation() != null) {
            addWarnings(document, plan.getValidation());
        }

        document.close();
        return baos.toByteArray();
    }

    // ── Cover ─────────────────────────────────────────────────────────────────

    private void addCover(Document doc, MetaDTO meta, String conversationId, String creatorName) {
        // Unsplash Background Image Cover
        try {
            String imageUrl = unsplashService.getPhotoUrl(meta.getDestination());
            byte[] imageBytes = unsplashService.downloadPhotoBytes(imageUrl);
            if (imageBytes != null) {
                com.itextpdf.io.image.ImageData imageData = com.itextpdf.io.image.ImageDataFactory.create(imageBytes);
                com.itextpdf.layout.element.Image img = new com.itextpdf.layout.element.Image(imageData);
                img.setFixedPosition(0, 0);
                img.scaleAbsolute(595, 842); // A4 page dimensions
                doc.add(img);

                // Add semi-transparent dark overlay on the canvas
                com.itextpdf.kernel.pdf.canvas.PdfCanvas canvas = new com.itextpdf.kernel.pdf.canvas.PdfCanvas(
                        doc.getPdfDocument().getFirstPage()
                );
                canvas.saveState();
                canvas.setFillColor(new com.itextpdf.kernel.colors.DeviceRgb(0, 0, 0));
                com.itextpdf.kernel.pdf.extgstate.PdfExtGState gs = new com.itextpdf.kernel.pdf.extgstate.PdfExtGState();
                gs.setFillOpacity(0.5f);
                canvas.setExtGState(gs);
                canvas.rectangle(0, 0, 595, 842);
                canvas.fill();
                canvas.restoreState();
            }
        } catch (Exception e) {
            log.warn("Could not load cover photo from Unsplash: {}", e.getMessage());
        }

        // Gray 900 header band with two columns: title and logo
        Table banner = new Table(UnitValue.createPercentArray(new float[] { 8, 2 }))
                .useAllAvailableWidth()
                .setBackgroundColor(new DeviceRgb(0x11, 0x18, 0x27))
                .setBorder(Border.NO_BORDER);

        Cell titleCell = new Cell()
                .add(new Paragraph("✈  TripBrain - " + meta.getDestination())
                        .setFont(fontBold).setFontSize(26).setFontColor(WHITE))
                .add(new Paragraph("Your personalised travel itinerary")
                        .setFont(fontItalic).setFontSize(13).setFontColor(new DeviceRgb(0xFB, 0xBF, 0x24)))
                .setBorder(Border.NO_BORDER)
                .setPadding(25);
        banner.addCell(titleCell);

        Cell logoCell = new Cell().setBorder(Border.NO_BORDER).setPadding(25);
        try (InputStream logoIs = getClass().getResourceAsStream("/static/apple-touch-icon.png")) {
            if (logoIs != null) {
                var logoData = com.itextpdf.io.image.ImageDataFactory.create(logoIs.readAllBytes());
                var logoImg = new com.itextpdf.layout.element.Image(logoData);
                logoImg.setWidth(50);
                logoImg.setHeight(50);
                logoCell.add(logoImg);
            }
        } catch (Exception e) {
            log.warn("Could not load logo: {}", e.getMessage());
        }
        banner.addCell(logoCell);

        doc.add(banner);
        doc.add(new Paragraph("\n"));

        // Key details grid
        Table grid = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
                .useAllAvailableWidth()
                .setBorder(Border.NO_BORDER);

        addCoverCell(grid, "From", meta.getSource());
        addCoverCell(grid, "To", meta.getDestination());
        addCoverCell(grid, "Dates", meta.getStartDate() + "  →  " + meta.getEndDate());
        addCoverCell(grid, "Duration", meta.getTotalDays() + " days");
        addCoverCell(grid, "Travellers", String.valueOf(meta.getHeadcount()));
        addCoverCell(grid, "Budget", meta.getBudgetPreference());
        addCoverCell(grid, "Max Budget", "₹" + String.format("%,.0f", meta.getMaxBudgetInr()));
        addCoverCell(grid, "Created By", creatorName);
        addCoverCell(grid, "Ref ID", conversationId.substring(0, 8).toUpperCase());

        doc.add(grid);
        doc.add(new Paragraph("\n\n"));

        doc.add(new Paragraph("Generated by TripBrain  •  " + LocalDate.now())
                .setFont(fontItalic).setFontSize(10).setFontColor(WHITE)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private void addCoverCell(Table table, String label, String value) {
        Cell cell = new Cell()
                .add(new Paragraph(label)
                        .setFont(fontRegular).setFontSize(9).setFontColor(TEXT_MUTED))
                .add(new Paragraph(value != null ? value : "—")
                        .setFont(fontBold).setFontSize(13).setFontColor(TEXT_DARK))
                .setBackgroundColor(BG_LIGHT)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(DIVIDER, 1))
                .setPadding(10)
                .setMargin(4);
        table.addCell(cell);
    }

    // ── Section Heading ───────────────────────────────────────────────────────

    private void addSectionHeading(Document doc, String title) {
        doc.add(new Paragraph(title)
                .setFont(fontBold)
                .setFontSize(16)
                .setFontColor(PRIMARY)
                .setBorderBottom(new SolidBorder(PRIMARY, 2))
                .setPaddingBottom(4)
                .setMarginBottom(10));
    }

    // ── Route Table ───────────────────────────────────────────────────────────

    private void addRouteTable(Document doc, List<RouteStopDTO> stops) {
        if (stops == null || stops.isEmpty())
            return;

        Table table = baseTable(new float[] { 0.5f, 2f, 2f, 1f, 2f, 2f });
        addHeaderRow(table, "#", "City", "Country", "Nights", "Check-in", "Check-out");

        for (RouteStopDTO stop : stops) {
            addDataRow(table,
                    String.valueOf(stop.getStopNumber()),
                    stop.getCity(),
                    stop.getCountry(),
                    String.valueOf(stop.getNights()),
                    stop.getCheckIn(),
                    stop.getCheckOut());
        }
        doc.add(table);
    }

    // ── Cost Table ────────────────────────────────────────────────────────────

    private void addCostTable(Document doc, CostBreakdownDTO cost, MetaDTO meta) {
        Table table = baseTable(new float[] { 3f, 2f });

        addCostRow(table, "Flights (Outbound)", cost.getFlights().getOutboundTotalInr());
        addCostRow(table, "Flights (Return)", cost.getFlights().getInboundTotalInr());
        addCostRow(table, "Inter-city Transport", cost.getFlights().getInterCityTotalInr());
        addCostRow(table, "Hotels", cost.getHotelsGrandTotalInr());
        addCostRow(table, "Food", cost.getFoodGrandTotalInr());
        addCostRow(table, "Activities", cost.getActivitiesGrandTotalInr());
        addCostRow(table, "Local Transport", cost.getLocalTransportGrandTotalInr());

        // Grand total row — highlighted
        Cell labelCell = new Cell()
                .add(new Paragraph("Grand Total")
                        .setFont(fontBold).setFontSize(11).setFontColor(WHITE))
                .setBackgroundColor(PRIMARY).setBorder(Border.NO_BORDER).setPadding(8);
        Cell valueCell = new Cell()
                .add(new Paragraph("₹" + String.format("%,.0f", cost.getGrandTotalInr()))
                        .setFont(fontBold).setFontSize(11).setFontColor(WHITE)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(PRIMARY).setBorder(Border.NO_BORDER).setPadding(8);
        table.addCell(labelCell);
        table.addCell(valueCell);

        // Budget remaining row
        boolean over = !cost.isWithinBudget();
        DeviceRgb remainBg = over ? WARNING_BG : new DeviceRgb(0xE8, 0xF5, 0xE9);
        DeviceRgb remainText = over ? WARNING_TEXT : new DeviceRgb(0x2E, 0x7D, 0x32);
        String remainLabel = over ? "Over Budget by" : "Budget Remaining";
        double remainAmt = Math.abs(cost.getBudgetRemainingInr());

        table.addCell(new Cell()
                .add(new Paragraph(remainLabel)
                        .setFont(fontBold).setFontSize(10).setFontColor(remainText))
                .setBackgroundColor(remainBg).setBorder(Border.NO_BORDER).setPadding(8));
        table.addCell(new Cell()
                .add(new Paragraph("₹" + String.format("%,.0f", remainAmt))
                        .setFont(fontBold).setFontSize(10).setFontColor(remainText)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(remainBg).setBorder(Border.NO_BORDER).setPadding(8));

        doc.add(table);
    }

    private void addCostRow(Table table, String label, Double value) {
        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(fontRegular).setFontSize(10).setFontColor(TEXT_DARK))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(DIVIDER, 0.5f))
                .setPadding(7));
        table.addCell(new Cell()
                .add(new Paragraph(value != null ? "₹" + String.format("%,.0f", value) : "—")
                        .setFont(fontRegular).setFontSize(10).setFontColor(TEXT_DARK)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(DIVIDER, 0.5f))
                .setPadding(7));
    }

    // ── Hotels Table ──────────────────────────────────────────────────────────

    private void addHotelsTable(Document doc, List<HotelDTO> hotels) {
        Table table = baseTable(new float[] { 2f, 1f, 2f, 1f, 1.5f, 1.5f });
        addHeaderRow(table, "Hotel", "Stars", "City", "Nights", "Per Night", "Total");

        for (HotelDTO h : hotels) {
            // Hotel Cell with dynamic Link
            Cell hotelCell = new Cell().setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7);
            if (h.getBookingUrl() != null && !h.getBookingUrl().isBlank()) {
                com.itextpdf.layout.element.Link link = new com.itextpdf.layout.element.Link(h.getName() != null ? h.getName() : "—", 
                        com.itextpdf.kernel.pdf.action.PdfAction.createURI(h.getBookingUrl()));
                link.setFont(fontBold).setFontSize(9).setFontColor(new DeviceRgb(59, 130, 246)).setUnderline();
                hotelCell.add(new Paragraph().add(link));
            } else {
                hotelCell.add(new Paragraph(h.getName() != null ? h.getName() : "—").setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK));
            }
            table.addCell(hotelCell);

            int starsCount = h.getStars() != null ? h.getStars() : 0;
            table.addCell(new Cell().add(new Paragraph("★".repeat(starsCount)).setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
            table.addCell(new Cell().add(new Paragraph(h.getCity() != null ? h.getCity() : "—").setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
            table.addCell(new Cell().add(new Paragraph(String.valueOf(h.getNights() != null ? h.getNights() : 0)).setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
            table.addCell(new Cell().add(new Paragraph("₹" + String.format("%,.0f", h.getRatePerNightInr() != null ? h.getRatePerNightInr() : 0.0)).setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
            table.addCell(new Cell().add(new Paragraph("₹" + String.format("%,.0f", h.getTotalCostInr() != null ? h.getTotalCostInr() : 0.0)).setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
        }
        doc.add(table);
    }

    // ── Transport Table ───────────────────────────────────────────────────────

    private void addTransportTable(Document doc, List<TransportLegDTO> legs) {
        Table table = baseTable(new float[] { 0.5f, 1.5f, 1.5f, 1f, 1.5f, 1.5f, 1.5f });
        addHeaderRow(table, "#", "From", "To", "Mode", "Date", "Duration", "Cost");

        for (TransportLegDTO leg : legs) {
            table.addCell(new Cell().add(new Paragraph(String.valueOf(leg.getLegNumber())).setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
            table.addCell(new Cell().add(new Paragraph(leg.getFrom() != null ? leg.getFrom() : "—").setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
            table.addCell(new Cell().add(new Paragraph(leg.getTo() != null ? leg.getTo() : "—").setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));

            // Mode Cell with Link
            Cell modeCell = new Cell().setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7);
            if (leg.getBookingUrl() != null && !leg.getBookingUrl().isBlank()) {
                com.itextpdf.layout.element.Link link = new com.itextpdf.layout.element.Link(leg.getMode() != null ? leg.getMode() : "—", 
                        com.itextpdf.kernel.pdf.action.PdfAction.createURI(leg.getBookingUrl()));
                link.setFont(fontBold).setFontSize(9).setFontColor(new DeviceRgb(59, 130, 246)).setUnderline();
                modeCell.add(new Paragraph().add(link));
            } else {
                modeCell.add(new Paragraph(leg.getMode() != null ? leg.getMode() : "—").setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK));
            }
            table.addCell(modeCell);

            table.addCell(new Cell().add(new Paragraph(leg.getDate() != null ? leg.getDate() : "—").setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
            table.addCell(new Cell().add(new Paragraph(leg.getDurationHrs() != null ? leg.getDurationHrs() + "h" : "—").setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
            table.addCell(new Cell().add(new Paragraph("₹" + String.format("%,.0f", leg.getCostTotalInr() != null ? leg.getCostTotalInr() : 0.0)).setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK)).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(DIVIDER, 0.5f)).setPadding(7));
        }
        doc.add(table);
    }

    // ── Day Card ──────────────────────────────────────────────────────────────

    private void addDayCard(Document doc, DayDTO day) {
        // Day header band
        Table header = new Table(UnitValue.createPercentArray(new float[] { 1f, 2f, 1f }))
                .useAllAvailableWidth()
                .setBackgroundColor(DAY_HEADER)
                .setBorder(new SolidBorder(PRIMARY, 1));

        header.addCell(new Cell()
                .add(new Paragraph("Day " + day.getDay())
                        .setFont(fontBold).setFontSize(13).setFontColor(PRIMARY))
                .setBorder(Border.NO_BORDER).setPadding(8));
        header.addCell(new Cell()
                .add(new Paragraph(day.getDate() + "  •  " + day.getBaseCity())
                        .setFont(fontRegular).setFontSize(10).setFontColor(TEXT_DARK))
                .setBorder(Border.NO_BORDER).setPadding(8).setVerticalAlignment(VerticalAlignment.MIDDLE));

        // Weather badge
        String weatherStr = "";
        if (day.getWeather() != null) {
            WeatherDTO w = day.getWeather();
            weatherStr = w.getCondition() + "  " + w.getTempRange()
                    + "  🌧 " + w.getRainProbabilityPct() + "%";
        }
        header.addCell(new Cell()
                .add(new Paragraph(weatherStr)
                        .setFont(fontItalic).setFontSize(9).setFontColor(TEXT_MUTED))
                .setBorder(Border.NO_BORDER).setPadding(8)
                .setTextAlignment(TextAlignment.RIGHT));

        doc.add(header);

        // Activities
        if (day.getActivities() != null) {
            for (ActivityDTO activity : day.getActivities()) {
                Table actRow = new Table(UnitValue.createPercentArray(new float[] { 0.3f, 3f, 1f }))
                        .useAllAvailableWidth()
                        .setBorder(Border.NO_BORDER)
                        .setBorderBottom(new SolidBorder(DIVIDER, 0.5f));

                actRow.addCell(new Cell()
                        .add(new Paragraph(iconForType(activity.getType()))
                                .setFontSize(14))
                        .setBorder(Border.NO_BORDER).setPadding(6));

                // Activity Name Paragraph with link to Google Maps if present
                Paragraph nameP = new Paragraph().setFont(fontBold).setFontSize(10);
                if (activity.getMapUrl() != null && !activity.getMapUrl().isBlank()) {
                    com.itextpdf.layout.element.Link mapLink = new com.itextpdf.layout.element.Link(activity.getName() != null ? activity.getName() : "—", 
                            com.itextpdf.kernel.pdf.action.PdfAction.createURI(activity.getMapUrl()));
                    mapLink.setFontColor(new DeviceRgb(59, 130, 246)).setUnderline();
                    nameP.add(mapLink);
                } else {
                    nameP.add(activity.getName() != null ? activity.getName() : "—").setFontColor(TEXT_DARK);
                }

                // Notes paragraph with extra booking link if present
                Paragraph noteP = new Paragraph(activity.getNotes() != null ? activity.getNotes() : "")
                        .setFont(fontItalic).setFontSize(8).setFontColor(TEXT_MUTED);
                if (activity.getBookingUrl() != null && !activity.getBookingUrl().isBlank()) {
                    com.itextpdf.layout.element.Link bookLink = new com.itextpdf.layout.element.Link(" [Book]", 
                            com.itextpdf.kernel.pdf.action.PdfAction.createURI(activity.getBookingUrl()));
                    bookLink.setFontColor(new DeviceRgb(245, 158, 11)).setUnderline();
                    noteP.add(bookLink);
                }

                actRow.addCell(new Cell()
                        .add(nameP)
                        .add(noteP)
                        .setBorder(Border.NO_BORDER).setPadding(6));

                actRow.addCell(new Cell()
                        .add(new Paragraph("₹" + String.format("%,.0f", activity.getCostTotalInr() != null ? activity.getCostTotalInr() : 0.0))
                                .setFont(fontBold).setFontSize(10).setFontColor(ACCENT)
                                .setTextAlignment(TextAlignment.RIGHT))
                        .add(new Paragraph((activity.getStartTime() != null ? activity.getStartTime() : "—") + "  •  " + (activity.getDurationHrs() != null ? activity.getDurationHrs() : 0.0) + "h")
                                .setFont(fontRegular).setFontSize(8).setFontColor(TEXT_MUTED)
                                .setTextAlignment(TextAlignment.RIGHT))
                        .setBorder(Border.NO_BORDER).setPadding(6));

                doc.add(actRow);
            }
        }

        // Day cost summary footer
        if (day.getDayCostSummary() != null) {
            DayCostSummaryDTO s = day.getDayCostSummary();
            Table footer = new Table(UnitValue.createPercentArray(new float[] { 1, 1, 1, 1 }))
                    .useAllAvailableWidth()
                    .setBackgroundColor(BG_LIGHT)
                    .setBorder(Border.NO_BORDER);

            addFooterCell(footer, "Activities", s.getActivitiesTotalInr());
            addFooterCell(footer, "Food", s.getFoodTotalInr());
            addFooterCell(footer, "Transport", s.getTransportTotalInr());
            addFooterCell(footer, "Day Total", s.getDayTotalInr());

            doc.add(footer);
        }

        doc.add(new Paragraph("\n"));
    }

    private void addFooterCell(Table table, String label, Double value) {
        table.addCell(new Cell()
                .add(new Paragraph(label)
                        .setFont(fontRegular).setFontSize(8).setFontColor(TEXT_MUTED))
                .add(new Paragraph(value != null ? "₹" + String.format("%,.0f", value) : "—")
                        .setFont(fontBold).setFontSize(9).setFontColor(TEXT_DARK))
                .setBorder(Border.NO_BORDER)
                .setPadding(6));
    }

    // ── Warnings ──────────────────────────────────────────────────────────────

    private void addWarnings(Document doc, ValidationDTO validation) {
        List<String> warnings = validation.getWarnings();
        if (warnings == null || warnings.isEmpty())
            return;

        addSectionHeading(doc, "Warnings & Notes");

        for (String warning : warnings) {
            doc.add(new Paragraph("⚠  " + warning)
                    .setFont(fontRegular)
                    .setFontSize(10)
                    .setFontColor(WARNING_TEXT)
                    .setBackgroundColor(WARNING_BG)
                    .setBorder(new SolidBorder(WARNING_TEXT, 0.5f))
                    .setPadding(8)
                    .setMarginBottom(6));
        }

        if (validation.getNotes() != null) {
            doc.add(new Paragraph(validation.getNotes())
                    .setFont(fontItalic).setFontSize(9).setFontColor(TEXT_MUTED)
                    .setMarginTop(6));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void initFonts() {
        try {
            try (InputStream is = getClass().getResourceAsStream("/fonts/Roboto-VariableFont_wdth,wght.ttf")) {
                if (is != null) {
                    fontRegular = PdfFontFactory.createFont(is.readAllBytes(), PdfEncodings.IDENTITY_H);
                }
            }
            try (InputStream is = getClass().getResourceAsStream("/fonts/static/Roboto-Bold.ttf")) {
                if (is != null) {
                    fontBold = PdfFontFactory.createFont(is.readAllBytes(), PdfEncodings.IDENTITY_H);
                }
            }
            try (InputStream is = getClass().getResourceAsStream("/fonts/static/Roboto-Italic.ttf")) {
                if (is != null) {
                    fontItalic = PdfFontFactory.createFont(is.readAllBytes(), PdfEncodings.IDENTITY_H);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load Roboto fonts, falling back to Helvetica", e);
        }
        try {
            if (fontRegular == null) fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            if (fontBold == null) fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            if (fontItalic == null) fontItalic = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);
        } catch (Exception e) {
            log.error("Failed to load standard fallback fonts", e);
        }
    }

    private void setupFontProvider(Document doc) {
        FontProvider fontProvider = new FontProvider();
        
        try (InputStream is = getClass().getResourceAsStream("/fonts/Roboto-VariableFont_wdth,wght.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
            }
        } catch (Exception e) {
            log.error("Failed to load Roboto Variable Font in TripPdfService", e);
        }

        try (InputStream is = getClass().getResourceAsStream("/fonts/Roboto-Italic-VariableFont_wdth,wght.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
            }
        } catch (Exception e) {
            log.error("Failed to load Roboto Italic Variable Font in TripPdfService", e);
        }

        try (InputStream is = getClass().getResourceAsStream("/fonts/static/Roboto-Bold.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
            }
        } catch (Exception e) {
            log.error("Failed to load Roboto Bold Static Font in TripPdfService", e);
        }

        try (InputStream is = getClass().getResourceAsStream("/fonts/NotoColorEmoji-Regular.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
            }
        } catch (Exception e) {
            log.error("Failed to load Noto Color Emoji Font in TripPdfService", e);
        }
        
        doc.setFontProvider(fontProvider);
        doc.setProperty(com.itextpdf.layout.properties.Property.FONT, new String[]{"Roboto", "Noto Color Emoji"});
    }

    private void addPageNumbers(PdfDocument pdfDoc) {
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new AbstractPdfDocumentEventHandler() {
            @Override
            public void onAcceptedEvent(AbstractPdfDocumentEvent event) {
                PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
                PdfPage page = docEvent.getPage();
                int pageNum = pdfDoc.getPageNumber(page);
                if (pageNum == 1) {
                    return;
                }
                PdfCanvas canvas = new PdfCanvas(page);
                Rectangle rect = page.getPageSize();

                try {
                    PdfFont font = fontRegular != null ? fontRegular : PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    canvas.beginText()
                            .setFontAndSize(font, 8)
                            .moveText(rect.getWidth() / 2 - 20, 20)
                            .showText("Page " + pageNum)
                            .endText()
                            .release();
                } catch (IOException ignored) {
                }
            }
        });
    }

    private Table baseTable(float[] widths) {
        return new Table(UnitValue.createPercentArray(widths))
                .useAllAvailableWidth()
                .setBorder(Border.NO_BORDER)
                .setMarginBottom(6);
    }

    private void addHeaderRow(Table table, String... headers) {
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h)
                            .setFont(fontBold).setFontSize(9).setFontColor(WHITE))
                    .setBackgroundColor(PRIMARY)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(7));
        }
    }

    private void addDataRow(Table table, String... values) {
        for (String v : values) {
            table.addCell(new Cell()
                    .add(new Paragraph(v != null ? v : "—")
                            .setFont(fontRegular).setFontSize(9).setFontColor(TEXT_DARK))
                    .setBorder(Border.NO_BORDER)
                    .setBorderBottom(new SolidBorder(DIVIDER, 0.5f))
                    .setPadding(7));
        }
    }

    private String iconForType(String type) {
        if (type == null)
            return "•";
        return switch (type.toUpperCase()) {
            case "OUTDOOR" -> "🌿";
            case "INDOOR" -> "🏛";
            case "FOOD" -> "🍜";
            case "NIGHTLIFE" -> "🌙";
            case "CULTURE" -> "🎭";
            case "ADVENTURE" -> "🧗";
            case "WELLNESS" -> "🧘";
            default -> "📍";
        };
    }

    private String generateTags(Conversation conversation, String markdown, String destination) {
        java.util.List<String> tagsList = new java.util.ArrayList<>();
        
        // 1. Add destination as a tag
        if (destination != null && !destination.isBlank()) {
            tagsList.add(destination);
        }
        
        // 2. Extract from TripRequest if available
        if (conversation != null) {
            tripRequestRepository.findByConversationId(conversation.getId()).ifPresent(req -> {
                if (req.getTravellerType() != null) {
                    tagsList.add(req.getTravellerType().name());
                }
                if (req.getBudgetPreference() != null) {
                    tagsList.add(req.getBudgetPreference().name());
                }
                if (req.getFoodStyles() != null) {
                    req.getFoodStyles().forEach(f -> tagsList.add(f.name()));
                }
                if (req.getVacationStyles() != null) {
                    req.getVacationStyles().forEach(v -> tagsList.add(v.name()));
                }
                if (req.getInterests() != null) {
                    req.getInterests().forEach(i -> tagsList.add(i.name()));
                }
            });
        }
        
        // 3. Scan markdown for typical keywords to add thematic tags
        if (markdown != null) {
            String lower = markdown.toLowerCase();
            if (lower.contains("beach") || lower.contains("ocean") || lower.contains("sea ") || lower.contains("coast")) {
                tagsList.add("Beach");
            }
            if (lower.contains("hike") || lower.contains("trek") || lower.contains("mountain") || lower.contains("climb") || lower.contains("adventure")) {
                tagsList.add("Adventure");
            }
            if (lower.contains("museum") || lower.contains("history") || lower.contains("art ") || lower.contains("temple") || lower.contains("palace") || lower.contains("culture")) {
                tagsList.add("Culture");
            }
            if (lower.contains("sushi") || lower.contains("seafood") || lower.contains("restaurant") || lower.contains("dinner") || lower.contains("food") || lower.contains("cuisine")) {
                tagsList.add("Food");
            }
            if (lower.contains("shopping") || lower.contains("market") || lower.contains("mall")) {
                tagsList.add("Shopping");
            }
            if (lower.contains("nightlife") || lower.contains("bar ") || lower.contains("club ") || lower.contains("pub ")) {
                tagsList.add("Nightlife");
            }
        }

        // Limit and clean list: keep unique values, format them nicely
        return tagsList.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .distinct()
                .limit(5)
                .collect(java.util.stream.Collectors.joining(","));
    }
}
