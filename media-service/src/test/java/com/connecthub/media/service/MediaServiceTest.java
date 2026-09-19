package com.connecthub.media.service;

import com.connecthub.media.entity.MediaFile;
import com.connecthub.media.exception.MediaPlanLimitException;
import com.connecthub.media.exception.MediaStorageQuotaException;
import com.connecthub.media.repository.MediaRepository;
import com.connecthub.media.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaRepository repo;
    @Mock
    private StorageProvider storage;
    @Mock
    private MediaUploadRateLimiter uploadRateLimiter;
    @InjectMocks
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "publicBaseUrl", "http://localhost:8080");
    }

    // ── upload ──────────────────────────────────────────────────────────────

    @Test
    void upload_textFile_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "hello world".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        MediaFile saved = MediaFile.builder().mediaId("abc")
                .url("http://localhost:8080/api/v1/media/file/abc").build();
        when(repo.save(any())).thenReturn(saved);

        MediaFile result = mediaService.upload(file, 1, "room1", "FREE");

        assertThat(result.getMediaId()).isEqualTo("abc");
        verify(storage).put(startsWith("files/"), any());
        verify(repo, times(2)).save(any()); // insert, then set the id-derived URLs
    }

    @Test
    void upload_imageFile_thumbnailFailureIsNonFatal() throws IOException {
        // Invalid JPEG bytes — Thumbnailator will throw, but upload proceeds
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "not-a-real-jpeg".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MediaFile result = mediaService.upload(file, 1, "room1", "FREE");

        assertThat(result).isNotNull();
        verify(storage, atLeastOnce()).put(startsWith("images/"), any());
    }

    @Test
    void upload_rateLimitExceeded_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(false);

        assertThrows(MediaPlanLimitException.class,
                () -> mediaService.upload(file, 1, "room1", "FREE"));
        verify(repo, never()).save(any());
    }

    @Test
    void upload_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);

        assertThrows(com.connecthub.media.exception.BadRequestException.class,
                () -> mediaService.upload(file, 1, "room1", "FREE"));
    }

    @Test
    void upload_fileTooLarge_throws() {
        // FREE tier per-file cap is 10MB (MediaTierLimits.FREE_MAX_FILE_SIZE_KB) — 11MB exceeds it
        byte[] big = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", big);
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);

        assertThrows(com.connecthub.media.exception.FileSizeLimitException.class,
                () -> mediaService.upload(file, 1, "room1", "FREE"));
    }

    @Test
    void upload_freeTierFileUnderCap_ok() {
        // 8MB is over the old flat 2MB constant but under the new 10MB FREE tier cap
        byte[] eightMb = new byte[8 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", eightMb);
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        when(repo.save(any())).thenReturn(MediaFile.builder().mediaId("free-ok").build());

        assertDoesNotThrow(() -> mediaService.upload(file, 1, "room1", "FREE"));
    }

    @Test
    void upload_premiumTierLargeFile_ok() {
        // 20MB would fail on FREE (10MB cap) but is fine on PREMIUM (250MB cap)
        byte[] twentyMb = new byte[20 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", twentyMb);
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        when(repo.save(any())).thenReturn(MediaFile.builder().mediaId("premium-ok").build());

        assertDoesNotThrow(() -> mediaService.upload(file, 1, "room1", "PREMIUM"));
    }

    @Test
    void upload_storageQuotaExceeded_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        // FREE cap is 100MB (102400 KB); return 102400 so adding even 1KB exceeds it
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(102400L);

        assertThrows(MediaStorageQuotaException.class,
                () -> mediaService.upload(file, 1, "room1", "FREE"));
    }

    @Test
    void upload_proTierStorageQuotaExceeded_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        // PRO cap is 10GB (10485760 KB)
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(10485760L);

        assertThrows(MediaStorageQuotaException.class,
                () -> mediaService.upload(file, 1, "room1", "PRO"));
    }

    @Test
    void upload_disallowedContentType_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream",
                "MZ".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);

        assertThrows(RuntimeException.class,
                () -> mediaService.upload(file, 1, "room1", "FREE"));
    }

    @Test
    void upload_nullContentType_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "f.bin", null, "data".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);

        assertThrows(RuntimeException.class,
                () -> mediaService.upload(file, 1, "room1", "FREE"));
    }

    @Test
    void upload_filenameWithSpecialChars_sanitized() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "my file (1).txt", "text/plain", "content".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MediaFile result = mediaService.upload(file, 1, "room1", "FREE");

        // Sanitized name should not contain spaces or parentheses
        assertThat(result.getOriginalName()).doesNotContain(" ", "(", ")");
    }

    @Test
    void upload_nullFilename_usesDefault() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", null, "text/plain", "content".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MediaFile result = mediaService.upload(file, 1, "room1", "FREE");
        assertThat(result).isNotNull();
    }

    @Test
    void upload_storesStableGatewayUrlDerivedFromMediaId() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "hello".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        when(repo.save(any())).thenAnswer(inv -> {
            MediaFile mf = inv.getArgument(0);
            if (mf.getMediaId() == null) mf.setMediaId("id-123");
            return mf;
        });

        MediaFile result = mediaService.upload(file, 1, "room1", "FREE");

        assertThat(result.getUrl()).isEqualTo("http://localhost:8080/api/v1/media/file/id-123");
        assertThat(result.getThumbnailUrl()).isNull();
    }

    @Test
    void upload_image_getsThumbnailUrl() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(50, 50, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", out.toByteArray());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        when(repo.save(any())).thenAnswer(inv -> {
            MediaFile mf = inv.getArgument(0);
            if (mf.getMediaId() == null) mf.setMediaId("img-1");
            return mf;
        });

        byte[] thumbHead = new byte[2];
        doAnswer(inv -> {
            if (((String) inv.getArgument(0)).contains("/thumb_")) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream((java.nio.file.Path) inv.getArgument(1))) {
                    in.read(thumbHead);
                }
            }
            return null;
        }).when(storage).put(anyString(), any());

        MediaFile result = mediaService.upload(file, 1, "room1", "FREE");

        assertThat(result.getThumbnailUrl()).isEqualTo("http://localhost:8080/api/v1/media/file/img-1/thumb");
        verify(storage, times(2)).put(anyString(), any()); // original + thumbnail
        assertThat(thumbHead[0]).isEqualTo((byte) 0xFF); // thumbnail bytes really are a JPEG (FF D8)
        assertThat(thumbHead[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void upload_storageFailure_propagatesAndSavesNothing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "hello".getBytes());
        when(uploadRateLimiter.tryAcquire(any(), any())).thenReturn(true);
        when(repo.sumSizeKbByUploaderId(1)).thenReturn(0L);
        doThrow(new IOException("disk full")).when(storage).put(anyString(), any());

        assertThrows(IOException.class, () -> mediaService.upload(file, 1, "room1", "FREE"));
        verify(repo, never()).save(any());
    }

    // ── getById ─────────────────────────────────────────────────────────────

    @Test
    void getById_found_returnsPresent() {
        MediaFile mf = MediaFile.builder().mediaId("id1").build();
        when(repo.findById("id1")).thenReturn(Optional.of(mf));

        Optional<MediaFile> result = mediaService.getById("id1");

        assertThat(result).isPresent().contains(mf);
    }

    @Test
    void getById_notFound_returnsEmpty() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThat(mediaService.getById("missing")).isEmpty();
    }

    // ── getByRoom ────────────────────────────────────────────────────────────

    @Test
    void getByRoom_returnsList() {
        List<MediaFile> files = List.of(MediaFile.builder().mediaId("a").build());
        when(repo.findByRoomIdOrderByUploadedAtDesc("room1")).thenReturn(files);

        assertThat(mediaService.getByRoom("room1")).isEqualTo(files);
    }

    // ── count ────────────────────────────────────────────────────────────────

    @Test
    void count_returnsRepoValue() {
        when(repo.countByRoomId("room1")).thenReturn(7);

        assertThat(mediaService.count("room1")).isEqualTo(7);
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_withThumbnail_deletesMainAndThumb() throws Exception {
        MediaFile mf = MediaFile.builder()
                .mediaId("id1")
                .filename("images/uuid/photo.jpg")
                .originalName("photo.jpg")
                .thumbnailUrl("https://bucket/images/uuid/thumb_photo.jpg")
                .build();
        when(repo.findById("id1")).thenReturn(Optional.of(mf));

        mediaService.delete("id1");

        verify(storage).delete("images/uuid/photo.jpg");
        verify(storage).delete("images/uuid/thumb_photo.jpg");
        verify(repo).delete(mf);
    }

    @Test
    void delete_withoutThumbnail_deletesOnlyMain() throws Exception {
        MediaFile mf = MediaFile.builder()
                .mediaId("id1")
                .filename("files/uuid/doc.pdf")
                .originalName("doc.pdf")
                .thumbnailUrl(null)
                .build();
        when(repo.findById("id1")).thenReturn(Optional.of(mf));

        mediaService.delete("id1");

        verify(storage, times(1)).delete(anyString());
        verify(repo).delete(mf);
    }

    @Test
    void delete_notFound_noOp() throws Exception {
        when(repo.findById("ghost")).thenReturn(Optional.empty());

        mediaService.delete("ghost");

        verify(storage, never()).delete(anyString());
        verify(repo, never()).delete(any());
    }

    @Test
    void delete_storageFails_stillDeletesFromDb() throws Exception {
        MediaFile mf = MediaFile.builder()
                .mediaId("id1")
                .filename("files/uuid/doc.pdf")
                .originalName("doc.pdf")
                .thumbnailUrl(null)
                .build();
        when(repo.findById("id1")).thenReturn(Optional.of(mf));
        doThrow(new IOException("locked")).when(storage).delete(anyString());

        mediaService.delete("id1");

        verify(repo).delete(mf);
    }
}
