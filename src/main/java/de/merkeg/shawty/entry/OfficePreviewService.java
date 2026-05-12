package de.merkeg.shawty.entry;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;

/**
 * Converts Microsoft Office file formats to Markdown or HTML for in-browser preview.
 *
 * <ul>
 *   <li>DOCX → Markdown (headings, paragraphs, tables)</li>
 *   <li>XLSX / XLS → HTML table per sheet</li>
 *   <li>PPTX → Markdown (one section per slide)</li>
 * </ul>
 */
@ApplicationScoped
@Slf4j
public class OfficePreviewService {

    /** Maximum number of rows rendered per spreadsheet sheet. */
    private static final int MAX_SHEET_ROWS = 500;

    // ── Word ───────────────────────────────────────────────────────────────────

    /**
     * Converts a DOCX input stream to Markdown text.
     *
     * @param inputStream DOCX file stream (not closed by this method)
     * @return Markdown representation of the document
     */
    public String docxToMarkdown(InputStream inputStream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();

            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text == null || text.isBlank()) continue;

                String style = para.getStyle();
                int headingLevel = resolveHeadingLevel(style);
                if (headingLevel > 0) {
                    sb.append("#".repeat(headingLevel)).append(' ').append(text).append("\n\n");
                } else {
                    sb.append(text).append("\n\n");
                }
            }

            for (XWPFTable table : doc.getTables()) {
                boolean headerRow = true;
                for (XWPFTableRow row : table.getRows()) {
                    sb.append('|');
                    for (XWPFTableCell cell : row.getTableCells()) {
                        sb.append(' ').append(cell.getText().replace('\n', ' ')).append(" |");
                    }
                    sb.append('\n');
                    if (headerRow) {
                        sb.append('|');
                        for (int i = 0; i < row.getTableCells().size(); i++) {
                            sb.append(" --- |");
                        }
                        sb.append('\n');
                        headerRow = false;
                    }
                }
                sb.append('\n');
            }

            return sb.toString();
        }
    }

    // ── Spreadsheet ────────────────────────────────────────────────────────────

    /**
     * Converts an XLSX or XLS input stream to an HTML string containing one table per sheet.
     *
     * @param inputStream spreadsheet file stream (not closed by this method)
     * @return HTML fragment with one {@code <table>} per sheet
     */
    public String spreadsheetToHtml(InputStream inputStream) throws IOException {
        DataFormatter formatter = new DataFormatter();
        StringBuilder sb = new StringBuilder();

        try (var wb = WorkbookFactory.create(inputStream)) {
            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                sb.append("<div class=\"sheet-label\">").append(escapeHtml(sheet.getSheetName())).append("</div>");
                sb.append("<div class=\"sheet-scroll\"><table class=\"office-table\"><tbody>");

                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount++ >= MAX_SHEET_ROWS) {
                        sb.append("<tr><td colspan=\"999\" class=\"truncated-note\">Preview limited to ")
                          .append(MAX_SHEET_ROWS).append(" rows</td></tr>");
                        break;
                    }
                    sb.append("<tr>");
                    int lastCell = row.getLastCellNum();
                    for (int ci = 0; ci < lastCell; ci++) {
                        var cell = row.getCell(ci);
                        String val = cell != null ? formatter.formatCellValue(cell) : "";
                        sb.append(rowCount == 1 ? "<th>" : "<td>")
                          .append(escapeHtml(val))
                          .append(rowCount == 1 ? "</th>" : "</td>");
                    }
                    sb.append("</tr>");
                }

                sb.append("</tbody></table></div>");
            }
        }

        return sb.toString();
    }

    // ── PowerPoint ─────────────────────────────────────────────────────────────

    /**
     * Converts a PPTX input stream to Markdown text (one section per slide).
     *
     * @param inputStream PPTX file stream (not closed by this method)
     * @return Markdown representation of the presentation
     */
    public String pptxToMarkdown(InputStream inputStream) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            StringBuilder sb = new StringBuilder();
            int slideNum = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("## Slide ").append(slideNum++).append("\n\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String text = ts.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.trim()).append("\n\n");
                        }
                    }
                }
            }
            return sb.toString();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Extracts the heading level (1–6) from a Word paragraph style name such as
     * "Heading1", "Heading 2", "heading3", or returns 0 if not a heading.
     */
    private int resolveHeadingLevel(String style) {
        if (style == null) return 0;
        String lower = style.toLowerCase().replaceAll("\\s+", "");
        if (!lower.startsWith("heading")) return 0;
        String suffix = lower.substring("heading".length());
        if (suffix.isEmpty()) return 1;
        try {
            return Math.min(Integer.parseInt(suffix), 6);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

