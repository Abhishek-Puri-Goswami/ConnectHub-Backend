package com.connecthub.media.listener;

import com.connecthub.media.entity.MediaFile;
import com.connecthub.media.repository.MediaRepository;
import com.connecthub.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A room was deleted (room-service, topic room.deleted): delete the files shared in it, from disk and database.
 * Without this the bytes stayed on disk (and counted against the uploader's storage quota) forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomDeletionListener {

    private final MediaRepository mediaRepository;
    private final MediaService mediaService;

    @KafkaListener(topics = "room.deleted", groupId = "media-service-room-deletion-group", properties = "auto.offset.reset=earliest")
    @Transactional
    public void onRoomDeleted(String roomId) {
        if (roomId == null || roomId.isBlank() || "null".equals(roomId)) {
            log.error("Ignoring room.deleted event with no room id");
            return;
        }
        List<MediaFile> files = mediaRepository.findByRoomIdOrderByUploadedAtDesc(roomId.trim());
        for (MediaFile file : files) {
            mediaService.delete(file.getMediaId());
        }
        log.info("Removed {} files of deleted room {}", files.size(), roomId);
    }
}
