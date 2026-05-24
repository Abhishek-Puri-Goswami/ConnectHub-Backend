package com.connecthub.room.listener;

import com.connecthub.room.repository.RoomMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeletionListenerTest {

    @Mock private RoomMemberRepository roomMemberRepository;

    @InjectMocks private UserDeletionListener listener;

    @Test
    void onUserDeleted_validUserId_deletesAllMemberships() {
        listener.onUserDeleted("42");

        verify(roomMemberRepository).deleteByUserId(42);
    }

    @Test
    void onUserDeleted_invalidUserId_doesNotCallRepository() {
        listener.onUserDeleted("not-a-number");

        verify(roomMemberRepository, never()).deleteByUserId(anyInt());
    }

    @Test
    void onUserDeleted_repositoryThrows_propagatesException() {
        doThrow(new RuntimeException("db error")).when(roomMemberRepository).deleteByUserId(5);

        assertThrows(RuntimeException.class, () -> listener.onUserDeleted("5"));
    }

    @Test
    void onUserDeleted_nullPayload_doesNotCallRepository() {
        listener.onUserDeleted("null");

        verify(roomMemberRepository, never()).deleteByUserId(anyInt());
    }
}
