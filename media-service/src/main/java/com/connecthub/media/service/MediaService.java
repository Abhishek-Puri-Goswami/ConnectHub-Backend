package com.connecthub.media.service;

import com.connecthub.media.config.MediaTierLimits;
import com.connecthub.media.entity.MediaFile;
import com.connecthub.media.exception.BadRequestException;
import com.connecthub.media.exception.FileSizeLimitException;
import com.connecthub.media.exception.MediaPlanLimitException;
import com.connecthub.media.exception.MediaStorageQuotaException;
import com.connecthub.media.repository.MediaRepository;
import com.connecthub.media.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * MediaService — file upload, storage and retrieval (local disk via {@link StorageProvider})
 *
 * PURPOSE:
 *   Handles all media file operations for the chat: storing images and documents,
 *   generating image thumbnails, enforcing per-user storage quotas, and deleting files
 *   from both storage and the database. The stored URL ({@code <base>/api/v1/media/file/<id>})
 *   goes into the message's mediaUrl field; fetching it is authorized by MediaResource
 *   (room membership + a signed media-session cookie), so the URL itself is not a secret.
 *
 * UPLOAD FLOW (upload() method):
 *   1. RATE LIMIT per tier (MediaUploadRateLimiter). Reject with MediaPlanLimitException.
 *   2. VALIDATION: empty file -> 400; per-file size cap of the tier (10MB FREE, 250MB paid) -> 413.
 *   3. STORAGE QUOTA: user's stored KB + this file vs the tier cap -> MediaStorageQuotaException (413).
 *   4. CONTENT TYPE: only the ALLOWED MIME types; anything else -> 400.
 *   5. STORE: spool to a temp file, then StorageProvider.put("{images|videos|files}/{uuid}/{name}").
 *   6. THUMBNAIL: for images, a 300x300 JPEG stored as "thumb_{name}" (failure is non-fatal).
 *   7. PERSIST a MediaFile row (key in filename, public URLs derived from the generated id).
 *   8. CLEANUP: temp files are always deleted.
 *
 * FILE SANITIZATION:
 *   The original filename has everything except letters, digits, . _ - replaced by underscores,
 *   and LocalDiskStorageProvider additionally refuses any key that resolves outside its root.
 *
 * DELETION:
 *   delete() removes the file and its thumbnail from storage, then the database row.
 */
@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MediaService {

    private final MediaRepository repo;
    private final StorageProvider storage;
    private final MediaUploadRateLimiter uploadRateLimiter;

    /** Origin under which the gateway serves files; stored URLs are {@code <base>/api/v1/media/file/<id>}. */
    @Value("${media.public-base-url}")
    private String publicBaseUrl;

    /*
     * Image MIME types that receive thumbnail generation.
     * Non-image ALLOWED types (PDF, Word, ZIP, text, video) are stored as-is without thumbnails.
     */
    private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /** Video MIME types — larger size limit applies, stored under videos/ prefix in S3. */
    private static final Set<String> VIDEOS = Set.of(
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/x-ms-wmv", "video/3gpp");

    /*
     * Whitelist of permitted MIME types. Any content-type not in this set is rejected.
     * This prevents upload of potentially dangerous file types.
     */
    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip", "text/plain",
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/x-ms-wmv", "video/3gpp");

    /** Flat size cap for profile pictures only (not tier-based — avatars don't scale with plan). */
    private static final long MAX_SIZE = 2L * 1024 * 1024;

    /**
     * upload — validates, uploads to S3, generates a thumbnail if applicable, and persists the media record.
     *
     * @param file             the multipart file from the HTTP request
     * @param uploaderId       the authenticated user's ID (from X-User-Id header)
     * @param roomId           the room this file is being shared in
     * @param subscriptionTier the user's tier for rate limit and quota lookups
     * @return the persisted MediaFile record with S3 URL and thumbnail URL
     * @throws IOException if temp file creation or S3 upload fails
     */
    public MediaFile upload(MultipartFile file, int uploaderId, String roomId, String subscriptionTier) throws IOException {
        String tier = MediaTierLimits.normalizeTier(subscriptionTier);
        if (!uploadRateLimiter.tryAcquire(String.valueOf(uploaderId), tier)) {
            throw new MediaPlanLimitException("Upload rate limit exceeded for your plan");
        }
        if (file.isEmpty()) throw new BadRequestException("Empty file");
        long maxFileSizeKb = MediaTierLimits.maxFileSizeKb(tier);
        if (file.getSize() / 1024L > maxFileSizeKb) {
            String capHuman = maxFileSizeKb >= 1024L ? (maxFileSizeKb / 1024L) + " MB" : maxFileSizeKb + " KB";
            throw new FileSizeLimitException("File exceeds your plan's " + capHuman + " per-file limit. Upgrade to Pro for larger uploads.");
        }

        long capKb = MediaTierLimits.storageCapKb(tier);
        long usedKb = repo.sumSizeKbByUploaderId(uploaderId);
        long incomingKb = Math.max(1L, file.getSize() / 1024L);
        if (usedKb + incomingKb > capKb) {
            String capHuman = capKb >= 1024L * 1024L
                    ? (capKb / (1024L * 1024L)) + " GB"
                    : (capKb / 1024L) + " MB";
            throw new MediaStorageQuotaException(
                    "Storage quota exceeded for your plan (limit " + capHuman + " total). Delete files or upgrade your plan.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType))
            throw new BadRequestException("File type not allowed: " + contentType);

        /*
         * Sanitize the filename by replacing all non-safe characters with underscores.
         * This prevents path traversal and special character issues in S3 object keys.
         */
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_") : "file";
        String uuid = UUID.randomUUID().toString();
        String subDir = IMAGES.contains(contentType) ? "images" : VIDEOS.contains(contentType) ? "videos" : "files";
        String storageKey = subDir + "/" + uuid + "/" + originalName;

        /*
         * Spool to a temp file first: Thumbnailator needs a file path, and the storage
         * provider copies from it, so a failure halfway never leaves a partial object.
         */
        Path tempFile = Files.createTempFile("upload_", originalName); // NOSONAR java:S5443
        file.transferTo(tempFile.toFile());

        try {
            storage.put(storageKey, tempFile);

            /*
             * Thumbnail generation: for images, create a 300x300 JPEG using Thumbnailator
             * and store it next to the original as "thumb_{filename}".
             * Failures here are non-fatal — the upload proceeds without a thumbnail.
             */
            boolean hasThumbnail = false;
            String thumbKey = null;
            if (IMAGES.contains(contentType)) {
                Path tempThumb = null;
                try {
                    thumbKey = subDir + "/" + uuid + "/thumb_" + originalName;
                    tempThumb = Files.createTempFile("thumb_", ".jpg"); // NOSONAR java:S5443
                    Thumbnails.of(tempFile.toFile()).size(300, 300).outputFormat("jpg").toFile(tempThumb.toFile());
                    storage.put(thumbKey, tempThumb);
                    hasThumbnail = true;
                } catch (Exception e) {
                    log.warn("Thumbnail generation failed: {}", e.getMessage());
                } finally {
                    if (tempThumb != null) Files.deleteIfExists(tempThumb);
                }
            }

            MediaFile media = repo.save(MediaFile.builder()
                    .uploaderId(uploaderId).roomId(roomId)
                    .filename(storageKey)
                    .originalName(originalName)
                    .url("pending") // needs the generated id; set right below
                    .mimeType(contentType)
                    .sizeKb(file.getSize() / 1024)
                    .build());
            applyUrls(media, hasThumbnail);

            log.info("File stored: {} ({} KB) by user {}", originalName, media.getSizeKb(), uploaderId);
            return repo.save(media);

        } finally {
            /*
             * Always clean up the local temp file regardless of success or failure.
             * Without this, failed uploads would leave orphaned files in the OS temp directory.
             */
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * uploadProfilePicture — stores a user avatar image under the avatars/ prefix.
     * No room membership check or storage quota applies — profile pictures are a fixed
     * user-level asset. Only image types are accepted (jpeg, png, gif, webp).
     */
    public MediaFile uploadProfilePicture(MultipartFile file, int uploaderId) throws IOException {
        if (file.isEmpty()) throw new BadRequestException("Empty file");
        if (file.getSize() > MAX_SIZE) throw new FileSizeLimitException("Profile pictures are limited to 2 MB");
        String contentType = file.getContentType();
        if (contentType == null || !IMAGES.contains(contentType))
            throw new BadRequestException("Only image files are allowed for profile pictures");

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_") : "avatar";
        String uuid = UUID.randomUUID().toString();
        String storageKey = "avatars/" + uuid + "/" + originalName;
        Path tempFile = Files.createTempFile("avatar_", originalName); // NOSONAR java:S5443
        file.transferTo(tempFile.toFile());
        try {
            storage.put(storageKey, tempFile);
            MediaFile media = repo.save(MediaFile.builder()
                    .uploaderId(uploaderId).roomId(null)
                    .filename(storageKey).originalName(originalName)
                    .url("pending").mimeType(contentType)
                    .sizeKb(file.getSize() / 1024).build());
            applyUrls(media, false);
            log.info("Profile picture stored for user {}: {}", uploaderId, media.getUrl());
            return repo.save(media);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * getById — retrieves a single media file record by its database ID.
     * Used when the frontend needs metadata about a specific file (e.g., for a download link).
     */
    @Transactional(readOnly = true)
    public Optional<MediaFile> getById(String id) { return repo.findById(id); }

    /**
     * getByRoom — returns all media files shared in a room, newest first.
     * Used by the media gallery feature that shows all shared files/images in a sidebar panel.
     */
    @Transactional(readOnly = true)
    public List<MediaFile> getByRoom(String roomId) { return repo.findByRoomIdOrderByUploadedAtDesc(roomId); }

    /**
     * delete — removes a media file from S3 and from the database.
     *
     * HOW IT WORKS:
     *   1. Load the MediaFile record to get the S3 key (stored in the filename field).
     *   2. Delete the main file from S3.
     *   3. If a thumbnail exists (thumbnailUrl is not null), derive its S3 key by
     *      replacing the filename with "thumb_{filename}" and delete it too.
     *   4. Delete the database row.
     *   S3 deletion failures are caught and logged — the DB row is still deleted so
     *   orphaned S3 objects can be cleaned up separately if needed.
     */
    public void delete(String id) {
        repo.findById(id).ifPresent(f -> {
            try {
                storage.delete(f.getFilename());
                if (f.getThumbnailUrl() != null) storage.delete(thumbnailKey(f));
            } catch (Exception e) {
                log.warn("Failed to delete stored file {}: {}", f.getFilename(), e.getMessage());
            }
            repo.delete(f);
        });
    }

    /** Storage key of a file's thumbnail (stored next to the original as thumb_{name}). */
    public static String thumbnailKey(MediaFile f) {
        return f.getFilename().replace(f.getOriginalName(), "thumb_" + f.getOriginalName());
    }

    private void applyUrls(MediaFile media, boolean hasThumbnail) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        String url = base + "/api/v1/media/file/" + media.getMediaId();
        media.setUrl(url);
        media.setThumbnailUrl(hasThumbnail ? url + "/thumb" : null);
    }

    /**
     * count — returns the number of media files in a room.
     * Used by the room info panel to display the file/media count badge.
     */
    @Transactional(readOnly = true)
    public int count(String roomId) { return repo.countByRoomId(roomId); }

    /**
     * getTotalStorageMb — returns total storage used by all platform files in megabytes.
     * Converts from KB (stored in DB) to MB for the analytics dashboard.
     */
    @Transactional(readOnly = true)
    public double getTotalStorageMb() {
        return repo.sumAllSizeKb() / 1024.0;
    }
}
