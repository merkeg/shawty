package de.merkeg.shawty.entry;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Reads the first-level entry names from ZIP / TAR / TAR.GZ archives.
 *
 * <p>Directories are returned with a trailing {@code /}; plain files without.
 * Nested paths are collapsed to their top-level component (directory with {@code /} suffix).
 * The result is sorted: directories first, then files, both alphabetically.
 */
@ApplicationScoped
@Slf4j
public class ArchiveListingService {

    /**
     * Returns the first-level entries from the given archive stream, or {@code null} when the
     * archive type is not supported or reading fails.
     *
     * @param stream   the raw archive data (the caller is responsible for closing it)
     * @param filename the original filename – used to detect archive type by extension
     */
    public List<String> listFirstLevelEntries(InputStream stream, String filename) {
        if (filename == null) return null;
        String lower = filename.toLowerCase();
        try {
            if (lower.endsWith(".zip")) {
                return sortedEntries(readZip(stream));
            }
            if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
                return sortedEntries(readTar(new GZIPInputStream(stream)));
            }
            if (lower.endsWith(".tar")) {
                return sortedEntries(readTar(stream));
            }
        } catch (Exception e) {
            log.warn("Could not list archive entries for '{}': {}", filename, e.getMessage());
        }
        return null; // unsupported or failed – show "no preview" in UI
    }

    // ── Archive readers ────────────────────────────────────────────────────────

    private Set<String> readZip(InputStream stream) throws Exception {
        Set<String> firstLevel = new LinkedHashSet<>();
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(stream)) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String topLevel = extractTopLevel(entry.getName());
                if (!topLevel.isEmpty()) firstLevel.add(topLevel);
            }
        }
        return firstLevel;
    }

    private Set<String> readTar(InputStream stream) throws Exception {
        Set<String> firstLevel = new LinkedHashSet<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(stream)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                String topLevel = extractTopLevel(entry.getName());
                if (!topLevel.isEmpty()) firstLevel.add(topLevel);
            }
        }
        return firstLevel;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Extracts the top-level component of an archive path.
     *
     * <ul>
     *   <li>{@code "README.md"}         → {@code "README.md"}  (file)</li>
     *   <li>{@code "src/main/App.java"} → {@code "src/"}       (directory)</li>
     *   <li>{@code "src/"}              → {@code "src/"}        (directory)</li>
     * </ul>
     */
    private String extractTopLevel(String rawPath) {
        if (rawPath == null) return "";
        // Strip leading slashes (some archivers add them)
        String path = rawPath.replaceAll("^/+", "");
        if (path.isEmpty()) return "";

        // Strip trailing slash to find the first path separator
        String cleaned = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = cleaned.indexOf('/');
        if (slash < 0) {
            return path; // top-level file, keep as-is (no trailing slash)
        }
        // Nested – return the directory component with trailing slash
        return cleaned.substring(0, slash) + "/";
    }

    /**
     * Sorts entries: directories (ending with {@code /}) first, then plain files,
     * both groups sorted case-insensitively.
     */
    private List<String> sortedEntries(Set<String> entries) {
        return entries.stream()
                .sorted(Comparator
                        .<String, Boolean>comparing(s -> !s.endsWith("/")) // directories first (false < true)
                        .thenComparing(s -> s.toLowerCase()))
                .toList();
    }
}

