package com.connecthub.auth.service;

import com.connecthub.auth.client.MediaClient;
import com.connecthub.auth.client.MessageClient;
import com.connecthub.auth.client.PresenceClient;
import com.connecthub.auth.client.RoomClient;
import com.connecthub.auth.entity.AnalyticsSnapshot;
import com.connecthub.auth.repository.AnalyticsSnapshotRepository;
import com.connecthub.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private AnalyticsSnapshotRepository snapshotRepo;
    @Mock private UserRepository userRepo;
    @Mock private PresenceClient presenceClient;
    @Mock private MessageClient messageClient;
    @Mock private RoomClient roomClient;
    @Mock private MediaClient mediaClient;

    @InjectMocks private AnalyticsService analyticsService;

    @Test
    void snapshot_allClientsSucceed_savesFullSnapshot() {
        when(presenceClient.getOnlineCount()).thenReturn(42);
        when(userRepo.count()).thenReturn(1000L);
        when(userRepo.countByActiveTrue()).thenReturn(980L);
        when(userRepo.countByActiveFalse()).thenReturn(20L);
        when(messageClient.countToday()).thenReturn(500L);
        when(roomClient.countActiveRooms()).thenReturn(30L);
        when(mediaClient.getTotalStorageMb()).thenReturn(1024.5);
        when(snapshotRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        analyticsService.snapshot();

        ArgumentCaptor<AnalyticsSnapshot> cap = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo).save(cap.capture());
        AnalyticsSnapshot saved = cap.getValue();
        assertEquals(42, saved.getOnlineCount());
        assertEquals(1000L, saved.getTotalUsers());
        assertEquals(980L, saved.getActiveUsers());
        assertEquals(20L, saved.getSuspendedUsers());
        assertEquals(500L, saved.getMessagesToday());
        assertEquals(30L, saved.getActiveRooms());
        assertEquals(1024.5, saved.getStorageMb());
        verify(snapshotRepo).deleteOlderThan(any());
    }

    @Test
    void snapshot_presenceClientFails_defaultsOnlineToZero() {
        when(presenceClient.getOnlineCount()).thenThrow(new RuntimeException("timeout"));
        when(userRepo.count()).thenReturn(50L);
        when(userRepo.countByActiveTrue()).thenReturn(50L);
        when(userRepo.countByActiveFalse()).thenReturn(0L);
        when(messageClient.countToday()).thenReturn(10L);
        when(roomClient.countActiveRooms()).thenReturn(5L);
        when(mediaClient.getTotalStorageMb()).thenReturn(0.0);
        when(snapshotRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        analyticsService.snapshot();

        ArgumentCaptor<AnalyticsSnapshot> cap = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo).save(cap.capture());
        assertEquals(0, cap.getValue().getOnlineCount());
    }

    @Test
    void snapshot_messageClientFails_defaultsToZero() {
        when(presenceClient.getOnlineCount()).thenReturn(5);
        when(userRepo.count()).thenReturn(10L);
        when(userRepo.countByActiveTrue()).thenReturn(10L);
        when(userRepo.countByActiveFalse()).thenReturn(0L);
        when(messageClient.countToday()).thenThrow(new RuntimeException("unavailable"));
        when(roomClient.countActiveRooms()).thenReturn(2L);
        when(mediaClient.getTotalStorageMb()).thenReturn(0.0);
        when(snapshotRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        analyticsService.snapshot();

        ArgumentCaptor<AnalyticsSnapshot> cap = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo).save(cap.capture());
        assertEquals(0L, cap.getValue().getMessagesToday());
    }

    @Test
    void snapshot_roomClientFails_defaultsToZero() {
        when(presenceClient.getOnlineCount()).thenReturn(1);
        when(userRepo.count()).thenReturn(5L);
        when(userRepo.countByActiveTrue()).thenReturn(5L);
        when(userRepo.countByActiveFalse()).thenReturn(0L);
        when(messageClient.countToday()).thenReturn(1L);
        when(roomClient.countActiveRooms()).thenThrow(new RuntimeException("down"));
        when(mediaClient.getTotalStorageMb()).thenReturn(0.0);
        when(snapshotRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        analyticsService.snapshot();

        ArgumentCaptor<AnalyticsSnapshot> cap = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo).save(cap.capture());
        assertEquals(0L, cap.getValue().getActiveRooms());
    }

    @Test
    void snapshot_mediaClientFails_defaultsToZero() {
        when(presenceClient.getOnlineCount()).thenReturn(1);
        when(userRepo.count()).thenReturn(5L);
        when(userRepo.countByActiveTrue()).thenReturn(5L);
        when(userRepo.countByActiveFalse()).thenReturn(0L);
        when(messageClient.countToday()).thenReturn(1L);
        when(roomClient.countActiveRooms()).thenReturn(1L);
        when(mediaClient.getTotalStorageMb()).thenThrow(new RuntimeException("s3 error"));
        when(snapshotRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        analyticsService.snapshot();

        ArgumentCaptor<AnalyticsSnapshot> cap = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshotRepo).save(cap.capture());
        assertEquals(0.0, cap.getValue().getStorageMb());
    }

    @Test
    void snapshot_saveFails_doesNotPropagate() {
        when(presenceClient.getOnlineCount()).thenReturn(0);
        when(userRepo.count()).thenReturn(0L);
        when(userRepo.countByActiveTrue()).thenReturn(0L);
        when(userRepo.countByActiveFalse()).thenReturn(0L);
        when(messageClient.countToday()).thenReturn(0L);
        when(roomClient.countActiveRooms()).thenReturn(0L);
        when(mediaClient.getTotalStorageMb()).thenReturn(0.0);
        when(snapshotRepo.save(any())).thenThrow(new RuntimeException("db error"));

        assertDoesNotThrow(() -> analyticsService.snapshot());
    }

    @Test
    void getRecentSnapshots_delegatesToRepository() {
        List<AnalyticsSnapshot> expected = List.of(new AnalyticsSnapshot());
        when(snapshotRepo.findTop96ByOrderBySnapshotAtDesc()).thenReturn(expected);

        List<AnalyticsSnapshot> result = analyticsService.getRecentSnapshots();

        assertSame(expected, result);
    }
}
