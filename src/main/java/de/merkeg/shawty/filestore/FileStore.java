package de.merkeg.shawty.filestore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface FileStore {

    /**
     * Speichert eine Datei unter dem angegebenen Schlüssel.
     */
    void store(String key, File file, String contentType);

    /**
     * Öffnet einen Stream auf die gesamte Datei.
     * Caller ist für das Schließen des Streams verantwortlich.
     */
    InputStream openStream(String key) throws IOException;

    /**
     * Öffnet einen Stream auf einen Bytebereich der Datei (HTTP Range Requests).
     * Caller ist für das Schließen des Streams verantwortlich.
     *
     * @param start erster Byte-Index (inklusiv)
     * @param end   letzter Byte-Index (inklusiv)
     */
    InputStream openStream(String key, long start, long end) throws IOException;

    /**
     * Löscht eine Datei anhand des Schlüssels.
     */
    void delete(String key);
}
