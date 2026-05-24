package com.connecthub.notification.service;

import com.connecthub.notification.entity.Notification;
import com.connecthub.notification.entity.UserEmailPreference;
import com.connecthub.notification.repository.NotificationRepository;
import com.connecthub.notification.repository.UserEmailPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissedDmEmailSchedulerTest {

    @Mock private NotificationRepository notifRepo;
    @Mock private UserEmailPreferenceRepository emailPrefRepo;
    @Mock private EmailSender emailSender;

    @InjectMocks private MissedDmEmailScheduler scheduler;

    @Test
    void sendMissedNotificationEmails_noOptedInUsers_doesNothing() {
        when(emailPrefRepo.findByEmailNotificationsEnabledTrue()).thenReturn(List.of());

        scheduler.sendMissedNotificationEmails();

        verifyNoInteractions(notifRepo, emailSender);
    }

    @Test
    void sendMissedNotificationEmails_userWithPendingNotifications_sendsEmail() {
        UserEmailPreference pref = UserEmailPreference.builder()
                .userId(1).email("user@test.com").emailNotificationsEnabled(true).build();
        Notification notif = new Notification();
        when(emailPrefRepo.findByEmailNotificationsEnabledTrue()).thenReturn(List.of(pref));
        when(notifRepo.findDigestCandidates(eq(1), any())).thenReturn(List.of(notif));

        scheduler.sendMissedNotificationEmails();

        verify(emailSender).sendMissedNotifications(eq("user@test.com"), anyList());
        verify(notifRepo).markEmailSent(eq(1), any());
    }

    @Test
    void sendMissedNotificationEmails_userWithNoPendingNotifications_skipsEmail() {
        UserEmailPreference pref = UserEmailPreference.builder()
                .userId(2).email("user2@test.com").emailNotificationsEnabled(true).build();
        when(emailPrefRepo.findByEmailNotificationsEnabledTrue()).thenReturn(List.of(pref));
        when(notifRepo.findDigestCandidates(eq(2), any())).thenReturn(List.of());

        scheduler.sendMissedNotificationEmails();

        verifyNoInteractions(emailSender);
        verify(notifRepo, never()).markEmailSent(anyInt(), any());
    }

    @Test
    void sendMissedNotificationEmails_emailSenderFails_continuesWithOtherUsers() {
        UserEmailPreference pref1 = UserEmailPreference.builder()
                .userId(1).email("fail@test.com").emailNotificationsEnabled(true).build();
        UserEmailPreference pref2 = UserEmailPreference.builder()
                .userId(2).email("ok@test.com").emailNotificationsEnabled(true).build();
        Notification n = new Notification();
        when(emailPrefRepo.findByEmailNotificationsEnabledTrue()).thenReturn(List.of(pref1, pref2));
        when(notifRepo.findDigestCandidates(anyInt(), any())).thenReturn(List.of(n));
        doThrow(new RuntimeException("smtp error")).when(emailSender)
                .sendMissedNotifications(eq("fail@test.com"), any());

        // Should not throw — just continue to pref2
        scheduler.sendMissedNotificationEmails();

        verify(emailSender).sendMissedNotifications(eq("ok@test.com"), anyList());
    }
}
