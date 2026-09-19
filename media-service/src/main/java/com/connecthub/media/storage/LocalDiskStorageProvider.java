package com.connecthub.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stores media under a single root directory on the local disk (default {@code ~/connecthub-media},
 * override with MEDIA_STORAGE_DIR). Every key is resolved against the root and must stay inside it,
 * so a hostile filename or key can never escape the storage directory.
 */
@Component
@Slf4j
public class LocalDiskStorageProvider implements StorageProvider {

    private final Path root;

    public LocalDiskStorageProvider(@Value("${media.storage.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Media storage directory: {}", root);
    }

    @Override
    public void put(String key, Path source) throws IOException {
        Path target = safe(key);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Path resolve(String key) throws IOException {
        Path p = safe(key);
        if (!Files.isRegularFile(p)) throw new NoSuchFileException(key);
        return p;
    }

    @Override
    public boolean exists(String key) {
        try {
            return Files.isRegularFile(safe(key));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Path p = safe(key);
        Files.deleteIfExists(p);
        // tidy the now-empty per-upload folder (files/<uuid>/), never the root or a category dir
        Path parent = p.getParent();
        if (parent != null && !parent.equals(root) && parent.getParent() != null && !parent.getParent().equals(root)) {
            try {
                Files.deleteIfExists(parent); // only succeeds when empty
            } catch (IOException ignored) {
                // still holds another file (e.g. the thumbnail) — leave it
            }
        }
    }

    private Path safe(String key) throws IOException {
        if (key == null || key.isBlank()) throw new IOException("Empty storage key");
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root) || p.equals(root)) throw new IOException("Illegal storage key: " + key);
        return p;
    }
}
