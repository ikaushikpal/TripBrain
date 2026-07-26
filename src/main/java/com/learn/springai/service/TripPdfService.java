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
import org.springframework.web.server.ResponseStatusException;

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
import com.learn.springai.repository.ConversationRepository;
import com.learn.springai.repository.TripPdfRepository;

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

    @PostConstruct
    public void init() {
        log.info("UPLOAD DIR = {}", uploadDir);
    }

    // ─── Colours ────────────────────────────────────────────────────────────
    private static final DeviceRgb PRIMARY = new DeviceRgb(0x1A, 0x73, 0xE8);
    private static final DeviceRgb ACCENT = new DeviceRgb(0xFF, 0x6F, 0x00);
    private static final DeviceRgb BG_LIGHT = new DeviceRgb(0xF8, 0xF9, 0xFA);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(0x21, 0x21, 0x21);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(0x75, 0x75, 0x75);
    private static final DeviceRgb DIVIDER = new DeviceRgb(0xE0, 0xE0, 0xE0);
    private static final DeviceRgb WHITE = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb DAY_HEADER = new DeviceRgb(0xE8, 0xF0, 0xFE);
    private static final DeviceRgb WARNING_BG = new DeviceRgb(0xFF, 0xF3, 0xE0);
    private static final DeviceRgb WARNING_TEXT = new DeviceRgb(0xE6, 0x51, 0x00);

    // ─── Fonts ───────────────────────────────────────────────────────────────
    private PdfFont fontRegular;
    private PdfFont fontBold;
    private PdfFont fontItalic;

    // ────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ────────────────────────────────────────────────────────────────────────

    public TripPdf generateAndSave(String conversationId,
            TripPlanDTO plan,
            boolean isPublic) throws IOException {

        // 1. Resolve output path
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(uploadPath);

        String filename = conversationId + ".pdf";
        Path outputPath = uploadPath.resolve(filename);
        String publicUrl = "/resources/" + filename;

        // 2. Generate PDF bytes
        byte[] pdfBytes = buildPdf(plan, conversationId.toString());
        Files.write(outputPath, pdfBytes);
        log.info("PDF written → {}", outputPath);

        Conversation conversation = conversationRepository.getById(conversationId);

        // 3. Upsert metadata
        TripPdf entity = tripPdfRepository
                .findByConversationId(conversationId)
                .orElse(TripPdf.builder()
                        .conversation(conversation)
                        .build());

        entity.setFilePath(outputPath.toString());
        entity.setPublicUrl(publicUrl);
        entity.setPublic(isPublic);
        entity.setGeneratedAt(LocalDateTime.now());
        entity.setDestination(plan.getMeta().getDestination());

        return tripPdfRepository.save(entity);
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

    private byte[] buildPdf(TripPlanDTO plan, String conversationId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(40, 40, 50, 40);

        initFonts();
        addPageNumbers(pdfDoc);

        // ── Cover ────────────────────────────────────────────────────────────
        addCover(document, plan.getMeta(), conversationId);
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

    private void addCover(Document doc, MetaDTO meta, String conversationId) {
        // Dark header band
        Table banner = new Table(UnitValue.createPercentArray(new float[] { 1 }))
                .useAllAvailableWidth()
                .setBackgroundColor(PRIMARY)
                .setBorder(Border.NO_BORDER);

        Cell titleCell = new Cell()
                .add(new Paragraph("✈  TripBrain")
                        .setFont(fontBold).setFontSize(28).setFontColor(WHITE))
                .add(new Paragraph("Your personalised travel itinerary")
                        .setFont(fontItalic).setFontSize(13).setFontColor(new DeviceRgb(0xBB, 0xDE, 0xFB)))
                .setBorder(Border.NO_BORDER)
                .setPadding(30);
        banner.addCell(titleCell);
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
        addCoverCell(grid, "Ref ID", conversationId.substring(0, 8).toUpperCase());

        doc.add(grid);
        doc.add(new Paragraph("\n\n"));

        doc.add(new Paragraph("Generated by TripBrain  •  " + LocalDate.now())
                .setFont(fontItalic).setFontSize(9).setFontColor(TEXT_MUTED)
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
            addDataRow(table,
                    h.getName(),
                    "★".repeat(h.getStars()),
                    h.getCity(),
                    String.valueOf(h.getNights()),
                    "₹" + String.format("%,.0f", h.getRatePerNightInr()),
                    "₹" + String.format("%,.0f", h.getTotalCostInr()));
        }
        doc.add(table);
    }

    // ── Transport Table ───────────────────────────────────────────────────────

    private void addTransportTable(Document doc, List<TransportLegDTO> legs) {
        Table table = baseTable(new float[] { 0.5f, 1.5f, 1.5f, 1f, 1.5f, 1.5f, 1.5f });
        addHeaderRow(table, "#", "From", "To", "Mode", "Date", "Duration", "Cost");

        for (TransportLegDTO leg : legs) {
            addDataRow(table,
                    String.valueOf(leg.getLegNumber()),
                    leg.getFrom(),
                    leg.getTo(),
                    leg.getMode(),
                    leg.getDate(),
                    leg.getDurationHrs() + "h",
                    "₹" + String.format("%,.0f", leg.getCostTotalInr()));
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
                actRow.addCell(new Cell()
                        .add(new Paragraph(activity.getName())
                                .setFont(fontBold).setFontSize(10).setFontColor(TEXT_DARK))
                        .add(new Paragraph(activity.getNotes() != null ? activity.getNotes() : "")
                                .setFont(fontItalic).setFontSize(8).setFontColor(TEXT_MUTED))
                        .setBorder(Border.NO_BORDER).setPadding(6));
                actRow.addCell(new Cell()
                        .add(new Paragraph("₹" + String.format("%,.0f", activity.getCostTotalInr()))
                                .setFont(fontBold).setFontSize(10).setFontColor(ACCENT)
                                .setTextAlignment(TextAlignment.RIGHT))
                        .add(new Paragraph(activity.getStartTime() + "  •  " + activity.getDurationHrs() + "h")
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

    private void initFonts() throws IOException {
        fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        fontItalic = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);
    }

    private void addPageNumbers(PdfDocument pdfDoc) {
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new AbstractPdfDocumentEventHandler() {
            @Override
            public void onAcceptedEvent(AbstractPdfDocumentEvent event) {
                PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
                PdfPage page = docEvent.getPage();
                PdfCanvas canvas = new PdfCanvas(page);
                Rectangle rect = page.getPageSize();

                try {
                    PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    canvas.beginText()
                            .setFontAndSize(font, 8)
                            .moveText(rect.getWidth() / 2 - 20, 20)
                            .showText("Page " + pdfDoc.getPageNumber(page))
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
}
