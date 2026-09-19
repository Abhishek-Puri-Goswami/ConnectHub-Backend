package com.connecthub.notification.resource;

import com.connecthub.notification.entity.Notification;
import com.connecthub.notification.exception.ForbiddenException;
import com.connecthub.notification.exception.ResourceNotFoundException;
import com.connecthub.notification.repository.DeviceTokenRepository;
import com.connecthub.notification.repository.UserEmailPreferenceRepository;
import com.connecthub.notification.service.NotifService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotifResourceTest {

    @Mock
    private NotifService service;

    @Mock
    private DeviceTokenRepository deviceTokenRepo;

    @Mock
    private UserEmailPreferenceRepository emailPrefRepo;

    private NotifResource resource;

    @BeforeEach
    void setUp() {
        resource = new NotifResource(service, deviceTokenRepo, emailPrefRepo);
    }

    @Test
    void send_internalCallerOnly() {
        Notification input = new Notification();
        when(service.send(input)).thenReturn(input);

        ResponseEntity<Notification> response = resource.send(input, "websocket-service");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(input, response.getBody());
        assertThrows(ForbiddenException.class, () -> resource.send(input, null));
        verify(service, times(1)).send(input);
    }

    @Test
    void get_ownNotificationsOnly() {
        when(service.getByRecipient(7)).thenReturn(List.of(new Notification()));

        assertEquals(1, resource.get(7, 7).getBody().size());
        assertThrows(ForbiddenException.class, () -> resource.get(7, 8));
        verify(service, times(1)).getByRecipient(7);
    }

    @Test
    void read_ownerMarksRead_othersForbidden_missingIs404() {
        when(service.recipientOf(11)).thenReturn(Optional.of(5));
        when(service.recipientOf(12)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NO_CONTENT, resource.read(11, 5).getStatusCode());
        verify(service).markRead(11);
        assertThrows(ForbiddenException.class, () -> resource.read(11, 6));
        assertThrows(ResourceNotFoundException.class, () -> resource.read(12, 5));
        verify(service, times(1)).markRead(anyInt());
    }

    @Test
    void readAll_ownOnly() {
        assertEquals(HttpStatus.NO_CONTENT, resource.readAll(3, 3).getStatusCode());
        verify(service).markAllRead(3);
        assertThrows(ForbiddenException.class, () -> resource.readAll(3, 4));
        verify(service, times(1)).markAllRead(anyInt());
    }

    @Test
    void unread_ownOnly() {
        when(service.unreadCount(9)).thenReturn(4);

        assertEquals(4, resource.unread(9, 9).getBody());
        assertThrows(ForbiddenException.class, () -> resource.unread(9, 1));
    }

    @Test
    void del_ownerDeletes_othersForbidden() {
        when(service.recipientOf(15)).thenReturn(Optional.of(2));

        assertEquals(HttpStatus.NO_CONTENT, resource.del(15, 2).getStatusCode());
        verify(service).delete(15);
        assertThrows(ForbiddenException.class, () -> resource.del(15, 3));
        verify(service, times(1)).delete(anyInt());
    }

    @Test
    void removeDeviceToken_onlyCallersOwnToken() {
        assertEquals(HttpStatus.NO_CONTENT, resource.removeDeviceToken("tok", 4).getStatusCode());
        verify(deviceTokenRepo).deleteByFcmTokenAndUserId("tok", 4);
    }
}
