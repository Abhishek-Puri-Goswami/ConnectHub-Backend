package com.connecthub.media.storage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Where uploaded bytes live. media-service talks only to this interface, so the backing store
 * (local disk today) can be swapped without touching upload/serve logic.
 * Keys are relative, slash-separated paths such as {@code files/<uuid>/report.pdf}.
 */
public interface StorageProvider {

    /** Stores the file's bytes under the key, replacing anything already there. */
    void put(String key, Path source) throws IOException;

    /** Local file holding the object's bytes; throws if the key does not exist. */
    Path resolve(String key) throws IOException;

    boolean exists(String key);

    /** Removes the object; a missing key is not an error. */
    void delete(String key) throws IOException;
}
