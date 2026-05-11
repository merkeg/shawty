package de.merkeg.shawty.filestore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface FileStore {

    /**
     * Speichert eine Datei unter dem angegebenen Schlüssel.
     *
     * @param key         eindeutiger Schlüssel (z. B. entryId.ext)
     * @param file        zu speichernde Datei
     * @param contentType MIME-Type der Datei
     */
    void store(String key, File file, String contentType);

    /**
     * Lädt eine Datei anhand des Schlüssels.
     *
     * @param key eindeutiger Schlüssel
     * @return {@link StoredFile} mit Inhalt und Content-Type
     */
    StoredFile get(String key);

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
     * Lädt einen Bytebereich einer Datei (für HTTP Range Requests / Video-Streaming).
     *
     * @param key   eindeutiger Schlüssel
     * @param start erster Byte-Index (inklusiv)
     * @param end   letzter Byte-Index (inklusiv)
     */
    StoredFile getRange(String key, long start, long end);

    /**
     * Löscht eine Datei anhand des Schlüssels.
     *
     * @param key eindeutiger Schlüssel
     */
    void delete(String key);
}
