package com.connecthub.media.listener;

import com.connecthub.media.entity.MediaFile;
import com.connecthub.media.repository.MediaRepository;
import com.connecthub.media.service.MediaService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class RoomDeletionListenerTest {

    private final MediaRepository repo = mock(MediaRepository.class);
    private final MediaService service = mock(MediaService.class);
    private final RoomDeletionListener listener = new RoomDeletionListener(repo, service);

    @Test
    void roomDeleted_deletesEveryFileSharedInIt() {
        when(repo.findByRoomIdOrderByUploadedAtDesc("room-1")).thenReturn(List.of(
                MediaFile.builder().mediaId("a").build(), MediaFile.builder().mediaId("b").build()));

        listener.onRoomDeleted("room-1");

        verify(service).delete("a");
        verify(service).delete("b");
    }

    @Test
    void blankPayload_isIgnored() {
        listener.onRoomDeleted(null);
        listener.onRoomDeleted("null");
        verifyNoInteractions(repo, service);
    }
}
