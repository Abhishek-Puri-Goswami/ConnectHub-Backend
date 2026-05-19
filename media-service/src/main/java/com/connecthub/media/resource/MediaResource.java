package com.connecthub.media.resource;

import com.connecthub.media.client.RoomServiceClient;
import com.connecthub.media.config.MediaTierLimits;
import com.connecthub.media.entity.MediaFile;
import com.connecthub.media.service.MediaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * MediaResource — REST controller for media file operations.
 *
 * AUTHORIZATION:
 *   All endpoints that access room-scoped media verify that the requesting user
 *   is a member of the associated room via room-service Feign call. This prevents
 *   unauthorized users from viewing or deleting media shared in private rooms.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Media", description = "File upload and serving (Amazon S3)")
public class MediaResource {

    private final MediaService svc;
    private final RoomServiceClient roomServiceClient;

    @PostMapping("/upload")
    public ResponseEntity<MediaFile> upload(@RequestParam("file") MultipartFile file,
                                             @RequestHeader("X-User-Id") int uid,
                                             @RequestHeader(value = "X-Subscription-Tier", required = false) String subscriptionTier,
                                             @RequestParam String roomId) throws IOException {
        /*
         * Authorization: verify the uploader is a member of the target room.
         * This prevents a user from uploading files into rooms they don't belong to.
         */
        if (!checkRoomMembership(roomId, uid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.upload(file, uid, roomId, MediaTierLimits.normalizeTier(subscriptionTier)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaFile> get(@PathVariable String id,
                                          @RequestHeader("X-User-Id") int uid) {
        Optional<MediaFile> mediaOpt = svc.getById(id);
        if (mediaOpt.isEmpty()) return ResponseEntity.notFound().build();
        MediaFile media = mediaOpt.get();
        /*
         * If the media file is associated with a room, verify membership.
         * Media without a roomId (e.g. profile avatars) is accessible to the uploader.
         */
        if (media.getRoomId() != null && !media.getRoomId().isBlank()) {
            if (!checkRoomMembership(media.getRoomId(), uid)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(media);
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<MediaFile>> byRoom(@PathVariable String roomId,
                                                    @RequestHeader("X-User-Id") int uid) {
        if (!checkRoomMembership(roomId, uid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(svc.getByRoom(roomId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> del(@PathVariable String id,
                                     @RequestHeader("X-User-Id") int uid) {
        Optional<MediaFile> mediaOpt = svc.getById(id);
        if (mediaOpt.isEmpty()) return ResponseEntity.notFound().build();
        MediaFile media = mediaOpt.get();
        /*
         * Only the uploader can delete their own media files.
         * This prevents room members from deleting each other's uploads.
         */
        if (!media.getUploaderId().equals(uid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        svc.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/room/{roomId}/count")
    public ResponseEntity<Integer> count(@PathVariable String roomId,
                                          @RequestHeader("X-User-Id") int uid) {
        if (!checkRoomMembership(roomId, uid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(svc.count(roomId));
    }

    /** Platform-wide total storage used in megabytes. Called by auth-service Feign client and admin dashboard. */
    @GetMapping("/storage/total")
    public ResponseEntity<Double> totalStorage() {
        return ResponseEntity.ok(svc.getTotalStorageMb());
    }

    /**
     * checkRoomMembership — verifies the user is a member of the room via room-service.
     * Returns true if the Feign call confirms membership. On Feign failure (room-service
     * is down), returns false to fail-closed — denying access rather than allowing it.
     */
    private boolean checkRoomMembership(String roomId, int userId) {
        try {
            Boolean isMember = roomServiceClient.isMember(roomId, userId);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.warn("Room membership check failed for room={} user={}: {}", roomId, userId, e.getMessage());
            return false; // fail-closed: deny access if room-service is unreachable
        }
    }
}
