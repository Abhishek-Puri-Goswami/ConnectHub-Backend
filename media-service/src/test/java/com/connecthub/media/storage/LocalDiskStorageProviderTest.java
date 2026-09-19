package com.connecthub.media.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalDiskStorageProviderTest {

    @TempDir Path tmp;
    private Path root;
    private LocalDiskStorageProvider storage;

    @BeforeEach
    void setUp() throws IOException {
        root = tmp.resolve("store");
        storage = new LocalDiskStorageProvider(root.toString());
    }

    private Path source(String content) throws IOException {
        Path p = Files.createTempFile(tmp, "src", ".bin");
        Files.writeString(p, content);
        return p;
    }

    @Test
    void putThenResolve_roundTripsBytes() throws IOException {
        storage.put("files/u1/doc.txt", source("hello"));
        assertTrue(storage.exists("files/u1/doc.txt"));
        assertEquals("hello", Files.readString(storage.resolve("files/u1/doc.txt")));
    }

    @Test
    void put_overwritesExistingKey() throws IOException {
        storage.put("files/u1/doc.txt", source("v1"));
        storage.put("files/u1/doc.txt", source("v2"));
        assertEquals("v2", Files.readString(storage.resolve("files/u1/doc.txt")));
    }

    @Test
    void resolve_missingKey_throws() {
        assertThrows(NoSuchFileException.class, () -> storage.resolve("files/none/x.txt"));
        assertFalse(storage.exists("files/none/x.txt"));
    }

    @Test
    void pathTraversalKeys_areRefused() throws IOException {
        Path src = source("evil");
        for (String key : new String[]{"../escape.txt", "files/../../escape.txt", "/etc/passwd", "..", "", "  ", ".", "files/.."}) {
            assertThrows(IOException.class, () -> storage.put(key, src), "put " + key);
            assertThrows(IOException.class, () -> storage.resolve(key), "resolve " + key);
            assertThrows(IOException.class, () -> storage.delete(key), "delete " + key);
        }
        assertFalse(Files.exists(tmp.resolve("escape.txt")));
    }

    @Test
    void delete_removesFile_andEmptyUploadFolder_butNotCategoryOrRoot() throws IOException {
        storage.put("files/u1/doc.txt", source("x"));
        storage.delete("files/u1/doc.txt");
        assertFalse(storage.exists("files/u1/doc.txt"));
        assertFalse(Files.exists(root.resolve("files/u1")));
        assertTrue(Files.exists(root.resolve("files")));
        assertTrue(Files.exists(root));
    }

    @Test
    void delete_keepsFolderWhileAnotherFileRemains_andMissingKeyIsNoError() throws IOException {
        storage.put("images/u2/p.png", source("a"));
        storage.put("images/u2/thumb_p.png", source("b"));
        storage.delete("images/u2/p.png");
        assertTrue(storage.exists("images/u2/thumb_p.png"));
        assertDoesNotThrow(() -> storage.delete("images/u2/never-existed.png"));
    }
}
