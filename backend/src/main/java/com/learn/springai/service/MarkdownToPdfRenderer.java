package com.learn.springai.service;

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic Markdown → iText PDF renderer.
 *
 * Supported Markdown constructs:
 *   ---front-matter---   YAML-like key: value pairs for cover page
 *   # Heading           Big cover-style title (only on cover)
 *   ## Section          Blue section heading with underline
 *   ### Day N — City    Day card header (light-blue band)
 *   | col | col |       Table row (pipe-delimited)
 *   |---|---|           Table header separator (skipped)
 *   - item              Bulleted paragraph
 *   **bold** inline     Bold text run
 *   [text](url)         Clickable hyperlink
 *   plain line          Regular paragraph
 *   blank line          Vertical spacer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarkdownToPdfRenderer {

    private final UnsplashService unsplashService;

    // ── Colours ────────────────────────────────────────────────────────────────
    private static final DeviceRgb PRIMARY    = new DeviceRgb(0x1A, 0x73, 0xE8);
    private static final DeviceRgb ACCENT     = new DeviceRgb(0xFF, 0x6F, 0x00);
    private static final DeviceRgb BG_LIGHT   = new DeviceRgb(0xF8, 0xF9, 0xFA);
    private static final DeviceRgb TEXT_DARK  = new DeviceRgb(0x21, 0x21, 0x21);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(0x75, 0x75, 0x75);
    private static final DeviceRgb DIVIDER    = new DeviceRgb(0xE0, 0xE0, 0xE0);
    private static final DeviceRgb WHITE      = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb DAY_HEADER = new DeviceRgb(0xE8, 0xF0, 0xFE);
    private static final DeviceRgb DARK_COVER = new DeviceRgb(0x11, 0x18, 0x27);
    private static final DeviceRgb LINK_COLOR = new DeviceRgb(0x3B, 0x82, 0xF6);
    private static final DeviceRgb GOLD       = new DeviceRgb(0xFB, 0xBF, 0x24);

    // ── Fonts (initialized lazily per render call) ─────────────────────────────
    private PdfFont fontRegular;
    private PdfFont fontBold;
    private PdfFont fontItalic;

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders the given Markdown string to a PDF byte array.
     */
    public byte[] render(String markdown, String creatorName) throws IOException {
        initFonts();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc, PageSize.A4);
        doc.setMargins(40, 40, 50, 40);

        setupFontProvider(doc);

        addPageNumbers(pdfDoc);

        String[] lines = markdown.split("\n", -1);
        int i = 0;

        // ── Parse optional front-matter block ─────────────────────────────────
        Map<String, String> frontMatter = new HashMap<>();
        if (lines.length > 0 && lines[0].trim().equals("---")) {
            i = 1;
            while (i < lines.length && !lines[i].trim().equals("---")) {
                String fm = lines[i].trim();
                int colon = fm.indexOf(':');
                if (colon > 0) {
                    frontMatter.put(fm.substring(0, colon).trim(), fm.substring(colon + 1).trim());
                }
                i++;
            }
            i++; // skip closing ---
        }

        // ── Render cover page ─────────────────────────────────────────────────
        renderCoverPage(doc, pdfDoc, frontMatter, creatorName);
        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

        // ── Render body lines ─────────────────────────────────────────────────
        boolean inTable = false;
        List<String[]> tableRows = new ArrayList<>();
        boolean tableHasHeader = false;

        while (i < lines.length) {
            String raw = lines[i];
            String line = raw.stripTrailing();

            if (line.startsWith("| ") || line.startsWith("|")) {
                // Table row
                if (!inTable) {
                    inTable = true;
                    tableRows = new ArrayList<>();
                    tableHasHeader = false;
                }
                String trimmed = line.trim();
                if (trimmed.matches("\\|[-| :]+\\|")) {
                    // Header separator — mark that we've seen it
                    tableHasHeader = true;
                } else {
                    String[] cols = Arrays.stream(trimmed.split("\\|"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toArray(String[]::new);
                    tableRows.add(cols);
                }
            } else {
                // Flush pending table
                if (inTable) {
                    flushTable(doc, tableRows, tableHasHeader);
                    tableRows = new ArrayList<>();
                    tableHasHeader = false;
                    inTable = false;
                }

                if (line.startsWith("### ")) {
                    renderDayHeader(doc, line.substring(4).trim());
                } else if (line.startsWith("## ")) {
                    renderSectionHeading(doc, line.substring(3).trim());
                } else if (line.startsWith("# ")) {
                    // secondary title (body use only — cover handled separately)
                    doc.add(new Paragraph(line.substring(2).trim())
                            .setFont(fontBold).setFontSize(18).setFontColor(PRIMARY)
                            .setMarginTop(12).setMarginBottom(8));
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    renderBullet(doc, line.substring(2).trim());
                } else if (line.isBlank()) {
                    doc.add(new Paragraph("").setMarginBottom(6));
                } else {
                    renderBodyLine(doc, line);
                }
            }
            i++;
        }
        // Flush any trailing table
        if (inTable && !tableRows.isEmpty()) {
            flushTable(doc, tableRows, tableHasHeader);
        }

        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COVER PAGE
    // ─────────────────────────────────────────────────────────────────────────

    private void renderCoverPage(Document doc, PdfDocument pdfDoc, Map<String, String> fm, String creatorName) {
        String destination = fm.getOrDefault("destination", "Your Destination");
        String source      = fm.getOrDefault("source", "");
        String startDate   = fm.getOrDefault("start_date", "");
        String endDate     = fm.getOrDefault("end_date", "");
        String days        = fm.getOrDefault("total_days", "");
        String budget      = fm.getOrDefault("budget", "");
        String travellers  = fm.getOrDefault("travellers", "");
        String refId       = fm.getOrDefault("ref_id", "");

        // Try Unsplash background
        try {
            String imageUrl = unsplashService.getPhotoUrl(destination);
            byte[] imageBytes = unsplashService.downloadPhotoBytes(imageUrl);
            if (imageBytes != null) {
                var imageData = com.itextpdf.io.image.ImageDataFactory.create(imageBytes);
                var img = new com.itextpdf.layout.element.Image(imageData);
                img.setFixedPosition(0, 0);
                img.scaleAbsolute(595, 842);
                doc.add(img);

                // Dark overlay
                PdfCanvas canvas = new PdfCanvas(pdfDoc.getFirstPage());
                canvas.saveState();
                canvas.setFillColor(new DeviceRgb(0, 0, 0));
                var gs = new com.itextpdf.kernel.pdf.extgstate.PdfExtGState();
                gs.setFillOpacity(0.5f);
                canvas.setExtGState(gs);
                canvas.rectangle(0, 0, 595, 842);
                canvas.fill();
                canvas.restoreState();
            }
        } catch (Exception e) {
            log.warn("Cover photo unavailable: {}", e.getMessage());
        }

        Table banner = new Table(UnitValue.createPercentArray(new float[]{8, 2}))
                .useAllAvailableWidth()
                .setBackgroundColor(DARK_COVER)
                .setBorder(Border.NO_BORDER);

        Cell titleCell = new Cell()
                .add(new Paragraph("✈  TripBrain - " + destination)
                        .setFont(fontBold).setFontSize(26).setFontColor(WHITE))
                .add(new Paragraph("Your personalised travel itinerary")
                        .setFont(fontItalic).setFontSize(13).setFontColor(GOLD))
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

        Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth().setBorder(Border.NO_BORDER);
        addCoverCell(grid, "From", source);
        addCoverCell(grid, "To", destination);
        addCoverCell(grid, "Dates", startDate + " → " + endDate);
        addCoverCell(grid, "Duration", days + " days");
        addCoverCell(grid, "Travellers", travellers);
        addCoverCell(grid, "Budget", budget);
        addCoverCell(grid, "Created By", creatorName);
        if (!refId.isBlank()) addCoverCell(grid, "Ref ID", refId.substring(0, Math.min(8, refId.length())).toUpperCase());
        doc.add(grid);

        doc.add(new Paragraph("Generated by TripBrain  •  " + LocalDate.now())
                .setFont(fontItalic).setFontSize(10).setFontColor(WHITE)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(20));
    }

    private void addCoverCell(Table table, String label, String value) {
        Cell cell = new Cell()
                .add(new Paragraph(label).setFontSize(9).setFontColor(TEXT_MUTED))
                .add(new Paragraph(value != null && !value.isBlank() ? value : "—")
                        .setFont(fontBold).setFontSize(13).setFontColor(TEXT_DARK))
                .setBackgroundColor(BG_LIGHT)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(DIVIDER, 1))
                .setPadding(10).setMargin(4);
        table.addCell(cell);
    }

    private void renderSectionHeading(Document doc, String title) {
        doc.add(new Paragraph(title)
                .setFont(fontBold).setFontSize(16).setFontColor(PRIMARY)
                .setBorderBottom(new SolidBorder(PRIMARY, 2))
                .setPaddingBottom(4).setMarginTop(16).setMarginBottom(10));
    }

    private void renderDayHeader(Document doc, String title) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth()
                .setBackgroundColor(DAY_HEADER)
                .setBorder(new SolidBorder(PRIMARY, 1));
        header.addCell(new Cell()
                .add(new Paragraph(title).setFont(fontBold).setFontSize(12).setFontColor(PRIMARY))
                .setBorder(Border.NO_BORDER).setPadding(8));
        doc.add(header);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TABLE
    // ─────────────────────────────────────────────────────────────────────────

    private void flushTable(Document doc, List<String[]> rows, boolean hasHeader) {
        if (rows.isEmpty()) return;

        int cols = rows.stream().mapToInt(r -> r.length).max().orElse(1);
        float[] widths = new float[cols];
        Arrays.fill(widths, 1f);

        Table table = new Table(UnitValue.createPercentArray(widths))
                .useAllAvailableWidth().setBorder(Border.NO_BORDER).setMarginBottom(8);

        boolean firstRow = true;
        for (String[] row : rows) {
            boolean isHeader = firstRow && hasHeader;
            firstRow = false;
            for (int c = 0; c < cols; c++) {
                String val = c < row.length ? row[c] : "";
                Cell cell = new Cell()
                        .setBorder(Border.NO_BORDER)
                        .setBorderBottom(new SolidBorder(DIVIDER, isHeader ? 1.5f : 0.5f))
                        .setPadding(7);
                if (isHeader) {
                    cell.setBackgroundColor(PRIMARY);
                    cell.add(new Paragraph(val).setFont(fontBold).setFontSize(9).setFontColor(WHITE));
                } else {
                    Paragraph p = buildInlineParagraph(val, 9, TEXT_DARK);
                    cell.add(p);
                }
                table.addCell(cell);
            }
        }
        doc.add(table);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BULLET / BODY
    // ─────────────────────────────────────────────────────────────────────────

    private void renderBullet(Document doc, String text) {
        Paragraph p = new Paragraph("• ").setFont(fontBold).setFontSize(10).setFontColor(ACCENT);
        appendInlineSpans(p, text, 10, TEXT_DARK);
        p.setMarginLeft(16).setMarginBottom(4);
        doc.add(p);
    }

    private void renderBodyLine(Document doc, String text) {
        Paragraph p = buildInlineParagraph(text, 10, TEXT_DARK);
        p.setMarginBottom(4);
        doc.add(p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INLINE PARSING (bold + hyperlinks)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a Paragraph with inline bold (**text**) and hyperlink ([text](url)) spans.
     */
    private Paragraph buildInlineParagraph(String text, float size, DeviceRgb color) {
        Paragraph p = new Paragraph().setFontSize(size).setFontColor(color);
        appendInlineSpans(p, text, size, color);
        return p;
    }

    private void appendInlineSpans(Paragraph p, String text, float size, DeviceRgb color) {
        // Combined pattern: bold or link
        Pattern combined = Pattern.compile("\\*\\*([^*]+)\\*\\*|\\[([^\\]]+)\\]\\(([^)]+)\\)");
        Matcher m = combined.matcher(text);
        int last = 0;
        while (m.find()) {
            // Plain text before match
            if (m.start() > last) {
                p.add(new Text(text.substring(last, m.start())).setFontSize(size).setFontColor(color));
            }
            if (m.group(1) != null) {
                // Bold
                p.add(new Text(m.group(1)).setFont(fontBold).setFontSize(size).setFontColor(color));
            } else {
                // Hyperlink
                String linkText = m.group(2);
                String url = m.group(3);
                try {
                    Link link = new Link(linkText, PdfAction.createURI(url));
                    link.setFontSize(size).setFontColor(LINK_COLOR).setUnderline();
                    p.add(link);
                } catch (Exception e) {
                    p.add(new Text(linkText).setFontSize(size).setFontColor(color));
                }
            }
            last = m.end();
        }
        // Remaining plain text
        if (last < text.length()) {
            p.add(new Text(text.substring(last)).setFontSize(size).setFontColor(color));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FONT + PAGE NUMBERS
    // ─────────────────────────────────────────────────────────────────────────

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
        
        // 1. Load Roboto Variable Font (Regular)
        try (InputStream is = getClass().getResourceAsStream("/fonts/Roboto-VariableFont_wdth,wght.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
                log.info("Loaded Roboto Variable Font");
            }
        } catch (Exception e) {
            log.error("Failed to load Roboto Variable Font", e);
        }
 
        // 2. Load Roboto Italic Variable Font
        try (InputStream is = getClass().getResourceAsStream("/fonts/Roboto-Italic-VariableFont_wdth,wght.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
                log.info("Loaded Roboto Italic Variable Font");
            }
        } catch (Exception e) {
            log.error("Failed to load Roboto Italic Variable Font", e);
        }
 
        // 3. Load Roboto Bold Font (static)
        try (InputStream is = getClass().getResourceAsStream("/fonts/static/Roboto-Bold.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
                log.info("Loaded Roboto Bold Static Font");
            }
        } catch (Exception e) {
            log.error("Failed to load Roboto Bold Static Font", e);
        }
 
        // 4. Load Noto Color Emoji Font
        try (InputStream is = getClass().getResourceAsStream("/fonts/NotoColorEmoji-Regular.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
                log.info("Loaded Noto Color Emoji Font");
            }
        } catch (Exception e) {
            log.error("Failed to load Noto Color Emoji Font", e);
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
                } catch (IOException ignored) {}
            }
        });
    }
}
