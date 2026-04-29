package com.connecthub.notification.service;

import com.connecthub.notification.repository.DeviceTokenRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FcmService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final FirebaseApp firebaseApp;

    @Autowired
    public FcmService(DeviceTokenRepository deviceTokenRepository,
                      @Autowired(required = false) FirebaseApp firebaseApp) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.firebaseApp = firebaseApp;
    }

    /**
     * sendPush — sends a multicast push to the given FCM token list.
     * Tokens that are no longer registered (device uninstalled the app) are
     * automatically deleted from the database on UNREGISTERED error.
     */
    public void sendPush(List<String> fcmTokens, String title, String body, Map<String, String> data) {
        if (firebaseApp == null || fcmTokens == null || fcmTokens.isEmpty()) return;
        try {
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putAllData(data != null ? data : Map.of())
                    .addAllTokens(fcmTokens)
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance(firebaseApp).sendEachForMulticast(message);

            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse sr = responses.get(i);
                if (!sr.isSuccessful()) {
                    MessagingErrorCode code = sr.getException() != null ? sr.getException().getMessagingErrorCode() : null;
                    if (code == MessagingErrorCode.UNREGISTERED) {
                        // Token no longer valid — clean it up so we stop sending to it
                        deviceTokenRepository.deleteByFcmToken(fcmTokens.get(i));
                        log.debug("Removed stale FCM token: {}", fcmTokens.get(i));
                    } else {
                        log.warn("FCM delivery failed for token {}: {}", fcmTokens.get(i),
                                sr.getException() != null ? sr.getException().getMessage() : "unknown");
                    }
                }
            }
        } catch (Exception e) {
            log.error("FCM multicast send failed: {}", e.getMessage());
        }
    }
}
