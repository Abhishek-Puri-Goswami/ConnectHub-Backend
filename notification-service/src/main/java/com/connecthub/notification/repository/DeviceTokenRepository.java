package com.connecthub.notification.repository;

import com.connecthub.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    List<DeviceToken> findByUserId(int userId);
    void deleteByFcmToken(String fcmToken);
}
