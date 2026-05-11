package de.merkeg.shawty.filestore;

import java.io.File;

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
