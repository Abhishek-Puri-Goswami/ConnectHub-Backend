package com.connecthub.notification.listener;

import com.connecthub.notification.repository.DeviceTokenRepository;
import com.connecthub.notification.repository.NotificationRepository;
import com.connecthub.notification.repository.UserEmailPreferenceRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DeletionListenersTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final DeviceTokenRepository tokens = mock(DeviceTokenRepository.class);
    private final UserEmailPreferenceRepository prefs = mock(UserEmailPreferenceRepository.class);

    @Test
    void userDeleted_removesNotificationsDeviceTokensAndEmailPreference() {
        new UserDeletionListener(notifications, tokens, prefs).onUserDeleted("42");

        verify(notifications).deleteAllByRecipient(42);
        verify(tokens).deleteByUserId(42);
        verify(prefs).deleteById(42);
    }

    @Test
    void userDeleted_badPayload_touchesNothing() {
        UserDeletionListener l = new UserDeletionListener(notifications, tokens, prefs);
        l.onUserDeleted("not-a-number");
        l.onUserDeleted(null);
        verify(notifications, never()).deleteAllByRecipient(anyInt());
        verifyNoInteractions(tokens, prefs);
    }

    @Test
    void roomDeleted_removesNotificationsAboutIt_andIgnoresBlankIds() {
        RoomDeletionListener l = new RoomDeletionListener(notifications);
        l.onRoomDeleted("room-9");
        l.onRoomDeleted(null);
        l.onRoomDeleted(" ");
        verify(notifications).deleteAllByRoom("room-9");
        verify(notifications, never()).deleteAllByRoom(" ");
        verify(notifications, times(1)).deleteAllByRoom(anyString());
    }
}
