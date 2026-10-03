package com.learn.springai.service;

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
 * Premium, Deterministic Markdown -> iText PDF renderer.
 * Formats cover page, section headers, day badges, pipe tables,
 * bullets, numbered lists, blockquotes, bold/italic, and links.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarkdownToPdfRenderer {

    private final UnsplashService unsplashService;

    // ── Colours ────────────────────────────────────────────────────────────────
    private static final DeviceRgb PRIMARY     = new DeviceRgb(0x1A, 0x73, 0xE8);
    private static final DeviceRgb ACCENT      = new DeviceRgb(0xFF, 0x6F, 0x00);
    private static final DeviceRgb BG_LIGHT    = new DeviceRgb(0xF8, 0xF9, 0xFA);
    private static final DeviceRgb BG_CALLOUT  = new DeviceRgb(0xEE, 0xF2, 0xFF);
    private static final DeviceRgb TEXT_DARK   = new DeviceRgb(0x21, 0x21, 0x21);
    private static final DeviceRgb TEXT_MUTED  = new DeviceRgb(0x61, 0x61, 0x61);
    private static final DeviceRgb DIVIDER     = new DeviceRgb(0xE0, 0xE0, 0xE0);
    private static final DeviceRgb WHITE       = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb DAY_HEADER  = new DeviceRgb(0xE8, 0xF0, 0xFE);
    private static final DeviceRgb DARK_COVER  = new DeviceRgb(0x11, 0x18, 0x27);
    private static final DeviceRgb LINK_COLOR  = new DeviceRgb(0x25, 0x63, 0xEB);
    private static final DeviceRgb GOLD        = new DeviceRgb(0xFB, 0xBF, 0x24);

    // ── Fonts ──────────────────────────────────────────────────────────────────
    private PdfFont fontRegular;
    private PdfFont fontBold;
    private PdfFont fontItalic;
    private PdfFont fontBoldItalic;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] render(String markdown, String creatorName) throws IOException {
        initFonts();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc, PageSize.A4);
        doc.setMargins(40, 40, 50, 40);

        setupFontProvider(doc);
        addPageNumbers(pdfDoc);

        // Clean raw metadata tags and code fence wrappers before processing
        String cleanMarkdown = cleanMarkdownSource(markdown);
        String[] lines = cleanMarkdown.split("\n", -1);
        int i = 0;

        // ── Parse front-matter ────────────────────────────────────────────────
        Map<String, String> frontMatter = new HashMap<>();
        if (lines.length > 0 && lines[0].trim().equals("---")) {
            i = 1;
            while (i < lines.length && !lines[i].trim().equals("---")) {
                String fm = lines[i].trim();
                int colon = fm.indexOf(':');
                if (colon > 0) {
                    frontMatter.put(fm.substring(0, colon).trim().toLowerCase(), fm.substring(colon + 1).trim());
                }
                i++;
            }
            if (i < lines.length && lines[i].trim().equals("---")) {
                i++; // skip closing ---
            }
        }

        // Fill in missing front-matter fields by inspecting markdown body
        enrichFrontMatterFromBody(frontMatter, cleanMarkdown);

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
            String trimmed = line.trim();

            // Check if table row
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 2) {
                if (!inTable) {
                    inTable = true;
                    tableRows = new ArrayList<>();
                    tableHasHeader = false;
                }
                if (trimmed.matches("\\|[-| :]+\\|")) {
                    tableHasHeader = true;
                } else {
                    // Extract cells without filtering out empty columns
                    String inner = trimmed.substring(1, trimmed.length() - 1);
                    String[] cols = Arrays.stream(inner.split("\\|", -1))
                            .map(String::trim)
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

                if (trimmed.startsWith("### ")) {
                    renderDayHeader(doc, trimmed.substring(4).trim());
                } else if (trimmed.startsWith("## ")) {
                    renderSectionHeading(doc, trimmed.substring(3).trim());
                } else if (trimmed.startsWith("# ")) {
                    // Main title inside body
                    Paragraph titleP = new Paragraph().setFont(fontBold).setFontSize(18).setFontColor(PRIMARY)
                            .setMarginTop(12).setMarginBottom(8);
                    appendInlineSpans(titleP, trimmed.substring(2).trim(), 18, PRIMARY, true);
                    doc.add(titleP);
                } else if (trimmed.startsWith("#### ")) {
                    Paragraph subP = new Paragraph().setFont(fontBold).setFontSize(12).setFontColor(TEXT_DARK)
                            .setMarginTop(8).setMarginBottom(4);
                    appendInlineSpans(subP, trimmed.substring(5).trim(), 12, TEXT_DARK, true);
                    doc.add(subP);
                } else if (trimmed.startsWith("##### ") || trimmed.startsWith("###### ")) {
                    String headingText = trimmed.replaceFirst("^#{5,6}\\s*", "");
                    Paragraph subP = new Paragraph().setFont(fontBold).setFontSize(11).setFontColor(TEXT_MUTED)
                            .setMarginTop(6).setMarginBottom(3);
                    appendInlineSpans(subP, headingText, 11, TEXT_MUTED, true);
                    doc.add(subP);
                } else if (trimmed.matches("^[-*+]\\s+.*")) {
                    renderBullet(doc, trimmed.replaceFirst("^[-*+]\\s+", ""));
                } else if (trimmed.matches("^\\d+[.)]\\s+.*")) {
                    renderNumberedItem(doc, trimmed);
                } else if (trimmed.startsWith("> ")) {
                    renderBlockquote(doc, trimmed.substring(2).trim());
                } else if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                    renderDivider(doc);
                } else if (trimmed.isEmpty()) {
                    doc.add(new Paragraph("").setMarginBottom(4));
                } else {
                    renderBodyLine(doc, trimmed);
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
        String destination = cleanValue(fm.getOrDefault("destination", "Your Travel Itinerary"));
        String source      = cleanValue(fm.getOrDefault("source", "Origin"));
        String startDate   = cleanValue(fm.getOrDefault("start_date", ""));
        String endDate     = cleanValue(fm.getOrDefault("end_date", ""));
        String days        = cleanValue(fm.getOrDefault("total_days", ""));
        String budget      = cleanValue(fm.getOrDefault("budget", "Flexible"));
        String travellers  = cleanValue(fm.getOrDefault("travellers", "1"));
        String refId       = cleanValue(fm.getOrDefault("ref_id", ""));

        // Format dates
        String datesDisplay = "Upcoming / Flexible Dates";
        if (!startDate.isEmpty() && !startDate.equalsIgnoreCase("TBD") && !startDate.equalsIgnoreCase("Flexible")) {
            if (!endDate.isEmpty() && !endDate.equalsIgnoreCase("TBD") && !endDate.equalsIgnoreCase("Flexible")) {
                datesDisplay = startDate + "  →  " + endDate;
            } else {
                datesDisplay = startDate;
            }
        }

        String durationDisplay = !days.isEmpty() && !days.equals("0") ? days + " Days" : "Custom Duration";
        String travellersDisplay = !travellers.isEmpty() && !travellers.equals("0") ? travellers + " Guests" : "1 Traveler";
        String budgetDisplay = !budget.isEmpty() ? budget.toUpperCase() : "Mid-Range (Standard)";

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
                gs.setFillOpacity(0.55f);
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
                .add(new Paragraph("✈  TripBrain — " + destination)
                        .setFont(fontBold).setFontSize(24).setFontColor(WHITE))
                .add(new Paragraph("Personalised Travel Itinerary & City Guide")
                        .setFont(fontItalic).setFontSize(13).setFontColor(GOLD))
                .setBorder(Border.NO_BORDER)
                .setPadding(22);
        banner.addCell(titleCell);

        Cell logoCell = new Cell().setBorder(Border.NO_BORDER).setPadding(22);
        try (InputStream logoIs = getClass().getResourceAsStream("/static/apple-touch-icon.png")) {
            if (logoIs != null) {
                var logoData = com.itextpdf.io.image.ImageDataFactory.create(logoIs.readAllBytes());
                var logoImg = new com.itextpdf.layout.element.Image(logoData);
                logoImg.setWidth(48);
                logoImg.setHeight(48);
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
        addCoverCell(grid, "Destination", destination);
        addCoverCell(grid, "Travel Dates", datesDisplay);
        addCoverCell(grid, "Duration", durationDisplay);
        addCoverCell(grid, "Party Size", travellersDisplay);
        addCoverCell(grid, "Budget Tier", budgetDisplay);
        addCoverCell(grid, "Planner / Traveler", creatorName != null ? creatorName : "Traveler");
        if (!refId.isBlank()) {
            addCoverCell(grid, "Reference ID", refId.substring(0, Math.min(8, refId.length())).toUpperCase());
        } else {
            addCoverCell(grid, "Plan Status", "Confirmed & Ready");
        }
        doc.add(grid);

        doc.add(new Paragraph("Generated by TripBrain  •  " + LocalDate.now())
                .setFont(fontItalic).setFontSize(10).setFontColor(WHITE)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(30));
    }

    private void addCoverCell(Table table, String label, String value) {
        Cell cell = new Cell()
                .add(new Paragraph(label).setFont(fontRegular).setFontSize(9).setFontColor(TEXT_MUTED))
                .add(new Paragraph(value != null && !value.isBlank() ? value : "—")
                        .setFont(fontBold).setFontSize(12).setFontColor(TEXT_DARK))
                .setBackgroundColor(BG_LIGHT)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(DIVIDER, 1))
                .setPadding(10).setMargin(4);
        table.addCell(cell);
    }

    private String cleanValue(String val) {
        if (val == null) return "";
        return val.replaceAll("^[\"']|[\"']$", "").trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION & BLOCK ELEMENTS
    // ─────────────────────────────────────────────────────────────────────────

    private void renderSectionHeading(Document doc, String title) {
        Paragraph p = new Paragraph()
                .setFont(fontBold).setFontSize(15).setFontColor(PRIMARY)
                .setBorderBottom(new SolidBorder(PRIMARY, 2))
                .setPaddingBottom(4).setMarginTop(14).setMarginBottom(8);
        appendInlineSpans(p, title, 15, PRIMARY, true);
        doc.add(p);
    }

    private void renderDayHeader(Document doc, String title) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth()
                .setBackgroundColor(DAY_HEADER)
                .setBorder(new SolidBorder(PRIMARY, 1))
                .setMarginTop(10).setMarginBottom(6);

        Paragraph p = new Paragraph().setFont(fontBold).setFontSize(12).setFontColor(PRIMARY);
        appendInlineSpans(p, "📅  " + title, 12, PRIMARY, true);
        header.addCell(new Cell().add(p).setBorder(Border.NO_BORDER).setPadding(7));
        doc.add(header);
    }

    private void renderBullet(Document doc, String text) {
        Paragraph p = new Paragraph("• ").setFont(fontBold).setFontSize(10).setFontColor(ACCENT);
        appendInlineSpans(p, text, 10, TEXT_DARK, false);
        p.setMarginLeft(14).setMarginBottom(3);
        doc.add(p);
    }

    private void renderNumberedItem(Document doc, String text) {
        Pattern numPattern = Pattern.compile("^(\\d+[.)])\\s+(.*)$");
        Matcher m = numPattern.matcher(text);
        if (m.find()) {
            Paragraph p = new Paragraph(m.group(1) + " ").setFont(fontBold).setFontSize(10).setFontColor(PRIMARY);
            appendInlineSpans(p, m.group(2), 10, TEXT_DARK, false);
            p.setMarginLeft(14).setMarginBottom(3);
            doc.add(p);
        } else {
            renderBodyLine(doc, text);
        }
    }

    private void renderBlockquote(Document doc, String text) {
        Table quoteTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth()
                .setBackgroundColor(BG_CALLOUT)
                .setBorderLeft(new SolidBorder(PRIMARY, 3))
                .setBorderTop(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .setBorderBottom(Border.NO_BORDER)
                .setMarginTop(4).setMarginBottom(6);

        Paragraph p = new Paragraph().setFont(fontItalic).setFontSize(10).setFontColor(TEXT_DARK);
        appendInlineSpans(p, text, 10, TEXT_DARK, false);
        quoteTable.addCell(new Cell().add(p).setBorder(Border.NO_BORDER).setPadding(8));
        doc.add(quoteTable);
    }

    private void renderDivider(Document doc) {
        Table divTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth()
                .setBorderBottom(new SolidBorder(DIVIDER, 1))
                .setMarginTop(8).setMarginBottom(8);
        doc.add(divTable);
    }

    private void renderBodyLine(Document doc, String text) {
        Paragraph p = buildInlineParagraph(text, 10, TEXT_DARK);
        p.setMarginBottom(4);
        doc.add(p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TABLES
    // ─────────────────────────────────────────────────────────────────────────

    private void flushTable(Document doc, List<String[]> rows, boolean hasHeader) {
        if (rows.isEmpty()) return;

        int cols = rows.stream().mapToInt(r -> r.length).max().orElse(1);
        if (cols <= 0) return;

        float[] widths = new float[cols];
        Arrays.fill(widths, 1f);

        Table table = new Table(UnitValue.createPercentArray(widths))
                .useAllAvailableWidth().setBorder(Border.NO_BORDER).setMarginTop(6).setMarginBottom(8);

        boolean firstRow = true;
        for (String[] row : rows) {
            boolean isHeader = firstRow && hasHeader;
            firstRow = false;
            for (int c = 0; c < cols; c++) {
                String rawVal = c < row.length ? row[c] : "";
                String val = cleanInlineMarkdown(rawVal);
                Cell cell = new Cell()
                        .setBorder(Border.NO_BORDER)
                        .setBorderBottom(new SolidBorder(DIVIDER, isHeader ? 1.5f : 0.5f))
                        .setPadding(6);
                if (isHeader) {
                    cell.setBackgroundColor(PRIMARY);
                    Paragraph p = new Paragraph().setFont(fontBold).setFontSize(9).setFontColor(WHITE);
                    appendInlineSpans(p, val, 9, WHITE, true);
                    cell.add(p);
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
    // INLINE PARSING (Bold, Italic, Links, Code)
    // ─────────────────────────────────────────────────────────────────────────

    private Paragraph buildInlineParagraph(String text, float size, DeviceRgb color) {
        Paragraph p = new Paragraph().setFontSize(size).setFontColor(color);
        appendInlineSpans(p, text, size, color, false);
        return p;
    }

    private void appendInlineSpans(Paragraph p, String text, float size, DeviceRgb color, boolean parentIsBold) {
        if (text == null || text.isEmpty()) return;

        // Clean any leftover metadata tags
        String clean = text.replaceAll("\\[PDF_DOWNLOAD_METADATA:[^\\]]*\\]", "")
                           .replaceAll("\\[PDF_READY_DOWNLOAD\\]", "")
                           .replaceAll("\\[HOTEL_RECOMMENDATION_METADATA:[^\\]]*\\]", "")
                           .replaceAll("\\[VISA_ALERT_METADATA:[^\\]]*\\]", "")
                           .trim();

        // Pattern matching:
        // Group 1 & 2: [text](url)
        // Group 3: ***bold italic*** or ___bold italic___
        // Group 4: **bold** or __bold__
        // Group 5: *italic* or _italic_
        // Group 6: `code`
        Pattern pattern = Pattern.compile(
                "\\[([^\\]]+)\\]\\(([^)]+)\\)|" +
                "\\*\\*\\*([^*]+)\\*\\*\\*|" +
                "\\*\\*([^*]+)\\*\\*|__([^_]+)__|" +
                "\\*([^*]+)\\*|_([^_]+)_|" +
                "`([^`]+)`"
        );

        Matcher m = pattern.matcher(clean);
        int last = 0;
        while (m.find()) {
            // Plain text before match
            if (m.start() > last) {
                String plain = clean.substring(last, m.start());
                p.add(new Text(plain).setFont(parentIsBold ? fontBold : fontRegular).setFontSize(size).setFontColor(color));
            }

            if (m.group(1) != null && m.group(2) != null) {
                // Link [text](url)
                String linkText = m.group(1);
                String url = m.group(2).trim();
                try {
                    Link link = new Link(linkText, PdfAction.createURI(url));
                    link.setFont(fontBold).setFontSize(size).setFontColor(LINK_COLOR).setUnderline();
                    p.add(link);
                } catch (Exception e) {
                    p.add(new Text(linkText).setFont(fontBold).setFontSize(size).setFontColor(LINK_COLOR));
                }
            } else if (m.group(3) != null) {
                // Bold Italic
                p.add(new Text(m.group(3)).setFont(fontBoldItalic != null ? fontBoldItalic : fontBold).setFontSize(size).setFontColor(color));
            } else if (m.group(4) != null || m.group(5) != null) {
                // Bold
                String bText = m.group(4) != null ? m.group(4) : m.group(5);
                p.add(new Text(bText).setFont(fontBold).setFontSize(size).setFontColor(color));
            } else if (m.group(6) != null || m.group(7) != null) {
                // Italic
                String iText = m.group(6) != null ? m.group(6) : m.group(7);
                p.add(new Text(iText).setFont(fontItalic).setFontSize(size).setFontColor(color));
            } else if (m.group(8) != null) {
                // Inline Code
                p.add(new Text(m.group(8)).setFont(fontRegular).setFontSize(size - 0.5f).setFontColor(new DeviceRgb(0x47, 0x55, 0x69)));
            }

            last = m.end();
        }

        // Remaining plain text
        if (last < clean.length()) {
            p.add(new Text(clean.substring(last)).setFont(parentIsBold ? fontBold : fontRegular).setFontSize(size).setFontColor(color));
        }
    }

    private String cleanInlineMarkdown(String text) {
        if (text == null) return "";
        return text.trim();
    }

    private String cleanMarkdownSource(String markdown) {
        if (markdown == null) return "";
        return markdown
                .replaceAll("```markdown\\s*", "")
                .replaceAll("```\\s*", "")
                .replaceAll("~~~[a-zA-Z]*\\s*", "")
                .replaceAll("~~~\\s*", "")
                .replaceAll("\\[PDF_DOWNLOAD_METADATA:[^\\]]*\\]", "")
                .replaceAll("\\[PDF_READY_DOWNLOAD\\]", "")
                .replaceAll("\\[HOTEL_RECOMMENDATION_METADATA:[^\\]]*\\]", "")
                .replaceAll("\\[VISA_ALERT_METADATA:[^\\]]*\\]", "")
                .trim();
    }

    private void enrichFrontMatterFromBody(Map<String, String> fm, String markdown) {
        // Look for Overview table: | Field | Value |
        Pattern rowPattern = Pattern.compile("\\|\\s*([A-Za-z ]+)\\s*\\|\\s*([^|\\n]+)\\s*\\|");
        Matcher m = rowPattern.matcher(markdown);
        while (m.find()) {
            String key = m.group(1).trim().toLowerCase().replace(" ", "_");
            String val = m.group(2).trim();
            if (!fm.containsKey(key) && !val.equalsIgnoreCase("value") && !val.matches("[-:]+")) {
                fm.put(key, val);
            }
        }

        // Extract destination from title '# Trip Plan — Destination'
        if (!fm.containsKey("destination")) {
            Matcher destMatcher = Pattern.compile("# Trip Plan —\\s*([^\\n\\r#]+)").matcher(markdown);
            if (destMatcher.find()) {
                fm.put("destination", destMatcher.group(1).trim());
            }
        }

        // Extract duration from Day count if total_days is missing
        if (!fm.containsKey("total_days")) {
            Matcher dayMatcher = Pattern.compile("### Day (\\d+)").matcher(markdown);
            int maxDay = 0;
            while (dayMatcher.find()) {
                try {
                    int d = Integer.parseInt(dayMatcher.group(1));
                    if (d > maxDay) maxDay = d;
                } catch (NumberFormatException ignored) {}
            }
            if (maxDay > 0) {
                fm.put("total_days", String.valueOf(maxDay));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FONT INITIALIZATION & PAGE NUMBERS
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
            try (InputStream is = getClass().getResourceAsStream("/fonts/static/Roboto-BoldItalic.ttf")) {
                if (is != null) {
                    fontBoldItalic = PdfFontFactory.createFont(is.readAllBytes(), PdfEncodings.IDENTITY_H);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load Roboto fonts, falling back to standard Helvetica", e);
        }
        try {
            if (fontRegular == null) fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            if (fontBold == null) fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            if (fontItalic == null) fontItalic = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);
            if (fontBoldItalic == null) fontBoldItalic = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLDOBLIQUE);
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
        } catch (Exception ignored) {}

        try (InputStream is = getClass().getResourceAsStream("/fonts/static/Roboto-Bold.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
            }
        } catch (Exception ignored) {}

        try (InputStream is = getClass().getResourceAsStream("/fonts/static/Roboto-Italic.ttf")) {
            if (is != null) {
                fontProvider.addFont(FontProgramFactory.createFont(is.readAllBytes()));
            }
        } catch (Exception ignored) {}

        doc.setFontProvider(fontProvider);
        doc.setProperty(com.itextpdf.layout.properties.Property.FONT, new String[]{"Roboto", "Helvetica"});
    }

    private void addPageNumbers(PdfDocument pdfDoc) {
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new AbstractPdfDocumentEventHandler() {
            @Override
            public void onAcceptedEvent(AbstractPdfDocumentEvent event) {
                PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
                PdfPage page = docEvent.getPage();
                int pageNum = pdfDoc.getPageNumber(page);
                if (pageNum == 1) {
                    return; // Skip cover page
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
