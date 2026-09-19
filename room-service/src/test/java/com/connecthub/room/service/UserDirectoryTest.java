package com.connecthub.room.service;

import com.connecthub.room.client.AuthClient;
import com.connecthub.room.exception.BadRequestException;
import com.connecthub.room.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserDirectoryTest {

    private AuthClient auth;
    private UserDirectory dir;

    @BeforeEach
    void setUp() {
        auth = mock(AuthClient.class);
        dir = new UserDirectory(auth);
    }

    @Test
    void allExisting_passes_andCallerIdIsForwarded() {
        when(auth.getUsersByIds(anyList(), eq("1"))).thenReturn(List.of(Map.of("userId", 2), Map.of("userId", 3)));
        assertDoesNotThrow(() -> dir.requireExist(List.of(2, 3), 1));
        verify(auth).getUsersByIds(List.of(2, 3), "1");
    }

    @Test
    void unknownIds_areRejected_andAllOfThemAreNamed() {
        when(auth.getUsersByIds(anyList(), any())).thenReturn(List.of(Map.of("userId", 2)));
        BadRequestException ex = assertThrows(BadRequestException.class, () -> dir.requireExist(List.of(2, 9999, 5555), 1));
        assertTrue(ex.getMessage().contains("5555") && ex.getMessage().contains("9999"), ex.getMessage());
        assertFalse(ex.getMessage().contains("[2"), "existing ids must not be reported");
    }

    @Test
    void duplicatesAreCollapsed_beforeTheLookup() {
        when(auth.getUsersByIds(anyList(), any())).thenReturn(List.of(Map.of("userId", 2)));
        assertDoesNotThrow(() -> dir.requireExist(List.of(2, 2, 2), 1));
        verify(auth).getUsersByIds(List.of(2), "1");
    }

    @Test
    void emptyInput_needsNoLookup() {
        dir.requireExist(List.of(), 1);
        dir.requireExist(null, 1);
        verifyNoInteractions(auth);
    }

    @Test
    void authServiceDown_failsClosedWith503_notAcceptedUnchecked() {
        when(auth.getUsersByIds(anyList(), any())).thenThrow(new RuntimeException("connection refused"));
        assertThrows(ServiceUnavailableException.class, () -> dir.requireExist(List.of(2), 1));
    }

    @Test
    void nullOrMalformedAnswer_meansNothingWasVerified() {
        when(auth.getUsersByIds(anyList(), any())).thenReturn(null);
        assertThrows(BadRequestException.class, () -> dir.requireExist(List.of(2), 1));
    }
}
