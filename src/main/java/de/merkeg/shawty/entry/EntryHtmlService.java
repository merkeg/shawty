package de.merkeg.shawty.entry;

import de.merkeg.shawty.config.ApplicationConfig;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URLConnection;

@ApplicationScoped
public class EntryHtmlService {

    @Inject
    @Location("entry")
    Template entryTemplate;

    @Inject
    ApplicationConfig applicationConfig;

    public String buildPage(Entry entry) {
        String base       = applicationConfig.baseUrl();
        String pageUrl    = base + entry.getId();
        String rawUrl     = base + entry.getId() + "/raw";
        String downloadUrl = pageUrl + "?download=true";

        String contentType = URLConnection.guessContentTypeFromName(entry.getOriginalFilename());
        if (contentType == null) contentType = "application/octet-stream";

        boolean isImage    = contentType.startsWith("image/");
        boolean isVideo    = contentType.startsWith("video/");
        boolean isAudio    = contentType.startsWith("audio/");
        boolean isPdf      = contentType.equals("application/pdf");
        boolean hasPreview = isImage || isVideo || isAudio || isPdf;

        String ogImage     = isImage ? rawUrl : "";
        String twitterCard = isImage ? "summary_large_image" : "summary";
        String description = entry.getOriginalFilename() + " · " + formatSize(entry.getFileSize()) + " · Shared via shawty";
        String iconType    = resolveIconType(contentType, entry.getOriginalFilename());

        return entryTemplate
                .data("filename",      entry.getOriginalFilename())
                .data("extension",     entry.getExtension() != null ? entry.getExtension().toUpperCase() : "FILE")
                .data("formattedSize", formatSize(entry.getFileSize()))
                .data("rawUrl",        rawUrl)
                .data("downloadUrl",   downloadUrl)
                .data("pageUrl",       pageUrl)
                .data("contentType",   contentType)
                .data("isImage",       isImage)
                .data("isVideo",       isVideo)
                .data("isAudio",       isAudio)
                .data("isPdf",         isPdf)
                .data("hasPreview",    hasPreview)
                .data("ogImage",       ogImage)
                .data("twitterCard",   twitterCard)
                .data("description",   description)
                .data("iconType",      iconType)
                .render();
    }

    private String resolveIconType(String contentType, String filename) {
        if (contentType.startsWith("image/")) return "image";
        if (contentType.startsWith("video/")) return "video";
        if (contentType.startsWith("audio/")) return "audio";
        if (contentType.startsWith("text/"))  return "code";
        String fn = filename != null ? filename.toLowerCase() : "";
        if (fn.endsWith(".zip") || fn.endsWith(".tar") || fn.endsWith(".gz")
                || fn.endsWith(".rar") || fn.endsWith(".7z")) return "archive";
        if (fn.endsWith(".json") || fn.endsWith(".xml")
                || fn.endsWith(".yaml") || fn.endsWith(".yml")) return "code";
        return "file";
    }

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
