package de.merkeg.shawty.filestore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface FileStore {

    /**
     * Stores a file under the given key.
     *
     * @param key         unique storage key (e.g. entryId.ext)
     * @param file        file to store
     * @param contentType MIME type of the file
     */
    void store(String key, File file, String contentType);

    /**
     * Opens an input stream for the entire file.
     * The caller is responsible for closing the stream.
     *
     * @param key unique storage key
     */
    InputStream openStream(String key) throws IOException;

    /**
     * Opens an input stream for a byte range of the file (HTTP Range Requests / streaming).
     * The caller is responsible for closing the stream.
     *
     * @param key   unique storage key
     * @param start first byte index (inclusive)
     * @param end   last byte index (inclusive)
     */
    InputStream openStream(String key, long start, long end) throws IOException;

    /**
     * Deletes a file by its key.
     *
     * @param key unique storage key
     */
    void delete(String key);
}
