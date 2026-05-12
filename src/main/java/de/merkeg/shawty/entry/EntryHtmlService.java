package de.merkeg.shawty.entry;

import de.merkeg.shawty.config.ApplicationConfig;
import de.merkeg.shawty.filestore.FileStore;
import io.quarkus.qute.Location;
import io.quarkus.qute.RawString;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Slf4j
public class EntryHtmlService {

    private static final int MAX_PREVIEW_BYTES = 512 * 1024; // 512 KB

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "js", "jsx", "ts", "tsx", "py", "rs", "go", "kt", "rb", "php",
            "c", "h", "cpp", "cc", "cxx", "hpp", "cs", "swift", "scala", "clj",
            "json", "xml", "yaml", "yml", "toml", "ini", "env", "properties",
            "html", "htm", "css", "scss", "less", "sql", "sh", "bash", "zsh",
            "fish", "ps1", "bat", "cmd", "gradle", "groovy", "tf", "hcl",
            "dockerfile", "makefile", "gitignore", "editorconfig", "lua",
            "r", "m", "ex", "exs", "erl", "hs", "ml", "fs", "fsx", "dart", "nim"
    );

    // ── Preview templates (one per file type) ──────────────────────────────────

    @Inject @Location("preview/image")       Template imageTemplate;
    @Inject @Location("preview/video")       Template videoTemplate;
    @Inject @Location("preview/audio")       Template audioTemplate;
    @Inject @Location("preview/pdf")         Template pdfTemplate;
    @Inject @Location("preview/text")        Template textTemplate;
    @Inject @Location("preview/markdown")    Template markdownTemplate;
    @Inject @Location("preview/spreadsheet") Template spreadsheetTemplate;
    @Inject @Location("preview/archive")     Template archiveTemplate;
    @Inject @Location("preview/none")        Template noneTemplate;

    @Inject @Location("not-found") Template notFoundTemplate;

    @Inject ApplicationConfig applicationConfig;
    @Inject FileStore          fileStore;
    @Inject OfficePreviewService officePreviewService;

    // ── Public API ─────────────────────────────────────────────────────────────

    public String buildNotFoundPage() {
        return notFoundTemplate.render();
    }

    public String buildPage(Entry entry) {
        String base = applicationConfig.baseUrl();
        if (!base.endsWith("/")) base = base + "/";

        UriBuilder entryUri   = UriBuilder.fromUri(base).path(entry.getId());
        String     pageUrl    = entryUri.build().toString();
        String     rawUrl     = entryUri.clone().path("raw").build().toString();
        String     downloadUrl = entryUri.clone().path("download").build().toString();

        String contentType = entry.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(entry.getOriginalFilename());
        }
        if (contentType == null) contentType = "application/octet-stream";

        // ── File-type flags ────────────────────────────────────────────────────
        boolean isImage = contentType.startsWith("image/");
        boolean isVideo = contentType.startsWith("video/");
        boolean isAudio = contentType.startsWith("audio/");
        boolean isPdf   = contentType.equals("application/pdf");

        boolean isMarkdown    = isMarkdownFile(entry.getOriginalFilename());
        boolean isText        = !isMarkdown && isTextFile(contentType, entry.getOriginalFilename());
        boolean isWord        = isWordFile(entry.getOriginalFilename());
        boolean isSpreadsheet = isSpreadsheetFile(entry.getOriginalFilename());
        boolean isPowerPoint  = isPowerPointFile(entry.getOriginalFilename());
        boolean isArchive     = isArchiveFile(entry.getOriginalFilename());

        // ── Rendered content ───────────────────────────────────────────────────
        Object  renderedContent    = null;
        boolean isPreviewTruncated = false;
        String  prismLanguage      = "";

        if (isMarkdown || isText) {
            TextContent tc = loadTextContent(entry);
            if (tc == null) {
                isMarkdown = false;
                isText     = false;
            } else {
                isPreviewTruncated = tc.truncated();
                if (isMarkdown) {
                    renderedContent = renderMarkdown(tc.content());
                } else {
                    renderedContent = tc.content();
                    prismLanguage   = resolvePrismLanguage(entry.getOriginalFilename());
                }
            }
        } else if (isWord || isPowerPoint) {
            try (var stream = fileStore.openStream(entry.getStorageKey())) {
                String markdown = isWord
                        ? officePreviewService.docxToMarkdown(stream)
                        : officePreviewService.pptxToMarkdown(stream);
                renderedContent = renderMarkdown(markdown);
                isMarkdown = true;
            } catch (Exception e) {
                log.warn("Office preview failed for {}: {}", entry.getOriginalFilename(), e.getMessage());
                isWord       = false;
                isPowerPoint = false;
                isMarkdown   = false;
            }
        } else if (isSpreadsheet) {
            try (var stream = fileStore.openStream(entry.getStorageKey())) {
                renderedContent = new RawString(officePreviewService.spreadsheetToHtml(stream));
            } catch (Exception e) {
                log.warn("Spreadsheet preview failed for {}: {}", entry.getOriginalFilename(), e.getMessage());
                isSpreadsheet = false;
            }
        }

        // ── Archive entries ────────────────────────────────────────────────────
        List<String> archiveEntries    = isArchive ? entry.getArchiveEntries() : null;
        boolean      hasArchiveEntries = archiveEntries != null && !archiveEntries.isEmpty();

        // ── Template selection ─────────────────────────────────────────────────
        Template template;
        if      (isImage)      template = imageTemplate;
        else if (isVideo)      template = videoTemplate;
        else if (isAudio)      template = audioTemplate;
        else if (isPdf)        template = pdfTemplate;
        else if (isMarkdown)   template = markdownTemplate;
        else if (isText)       template = textTemplate;
        else if (isSpreadsheet) template = spreadsheetTemplate;
        else if (isArchive)    template = archiveTemplate;
        else                   template = noneTemplate;

        // ── Common meta ────────────────────────────────────────────────────────
        String ogImage     = isImage ? rawUrl : "";
        String twitterCard = isImage ? "summary_large_image" : "summary";
        String description = entry.getOriginalFilename()
                + " · " + formatSize(entry.getFileSize()) + " · Shared via shawty";
        String iconType    = resolveIconType(contentType, entry.getOriginalFilename());

        return template.instance()
                .data("filename",           entry.getOriginalFilename())
                .data("extension",          entry.getExtension() != null ? entry.getExtension().toUpperCase() : "FILE")
                .data("formattedSize",      formatSize(entry.getFileSize()))
                .data("rawUrl",             rawUrl)
                .data("downloadUrl",        downloadUrl)
                .data("pageUrl",            pageUrl)
                .data("contentType",        contentType)
                .data("ogImage",            ogImage)
                .data("twitterCard",        twitterCard)
                .data("description",        description)
                .data("iconType",           iconType)
                .data("isPreviewTruncated", isPreviewTruncated)
                // type-specific
                .data("renderedContent",    renderedContent)
                .data("prismLanguage",      prismLanguage)
                .data("archiveEntries",     archiveEntries)
                .data("hasArchiveEntries",  hasArchiveEntries)
                .render();
    }

    // ── Text detection ─────────────────────────────────────────────────────────

    private boolean isMarkdownFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    private boolean isTextFile(String contentType, String filename) {
        if (contentType.startsWith("text/")) return true;
        if (filename == null) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        return TEXT_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase());
    }

    private boolean isWordFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".doc");
    }

    private boolean isSpreadsheetFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private boolean isPowerPointFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".pptx") || lower.endsWith(".ppt");
    }

    private boolean isArchiveFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".zip") || lower.endsWith(".tar")
                || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
                || lower.endsWith(".rar") || lower.endsWith(".7z")
                || lower.endsWith(".gz");
    }

    // ── Content loading ────────────────────────────────────────────────────────

    private RawString renderMarkdown(String markdown) {
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().sanitizeUrls(true).build();
        return new RawString(renderer.render(parser.parse(markdown)));
    }

    private TextContent loadTextContent(Entry entry) {
        try (InputStream is = fileStore.openStream(entry.getStorageKey())) {
            byte[] preview  = is.readNBytes(MAX_PREVIEW_BYTES + 1);
            boolean truncated = preview.length > MAX_PREVIEW_BYTES;
            if (truncated) preview = Arrays.copyOf(preview, MAX_PREVIEW_BYTES);

            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String content = decoder.decode(ByteBuffer.wrap(preview)).toString();
            return new TextContent(content, truncated);
        } catch (CharacterCodingException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private record TextContent(String content, boolean truncated) {}

    // ── Prism.js language mapping ──────────────────────────────────────────────

    private String resolvePrismLanguage(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : filename.toLowerCase();
        return switch (ext) {
            case "js", "jsx"                -> "javascript";
            case "ts", "tsx"                -> "typescript";
            case "java"                     -> "java";
            case "py"                       -> "python";
            case "rs"                       -> "rust";
            case "go"                       -> "go";
            case "kt"                       -> "kotlin";
            case "rb"                       -> "ruby";
            case "php"                      -> "php";
            case "c", "h"                   -> "c";
            case "cpp", "cc", "cxx", "hpp"  -> "cpp";
            case "cs"                       -> "csharp";
            case "swift"                    -> "swift";
            case "scala"                    -> "scala";
            case "json"                     -> "json";
            case "xml"                      -> "xml";
            case "yaml", "yml"              -> "yaml";
            case "toml"                     -> "toml";
            case "html", "htm"              -> "html";
            case "css"                      -> "css";
            case "scss"                     -> "scss";
            case "sh", "bash", "zsh", "fish" -> "bash";
            case "ps1"                      -> "powershell";
            case "sql"                      -> "sql";
            case "gradle", "groovy"         -> "groovy";
            case "tf", "hcl"                -> "hcl";
            case "dart"                     -> "dart";
            case "r"                        -> "r";
            case "lua"                      -> "lua";
            case "ex", "exs"                -> "elixir";
            case "hs"                       -> "haskell";
            default                         -> "text";
        };
    }

    // ── Icon type ──────────────────────────────────────────────────────────────

    private String resolveIconType(String contentType, String filename) {
        if (contentType.startsWith("image/")) return "image";
        if (contentType.startsWith("video/")) return "video";
        if (contentType.startsWith("audio/")) return "audio";
        if (contentType.startsWith("text/"))  return "code";
        if (filename == null) return "file";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")
                || lower.endsWith(".rar") || lower.endsWith(".7z")) return "archive";
        if (lower.endsWith(".json") || lower.endsWith(".xml")
                || lower.endsWith(".yaml") || lower.endsWith(".yml")) return "code";
        return "file";
    }

    // ── Size formatting ────────────────────────────────────────────────────────

    public static String formatSize(Long bytes) {
        if (bytes == null) return "Unknown size";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }
}
