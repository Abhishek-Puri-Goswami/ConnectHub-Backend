package com.connecthub.notification.repository;
import com.connecthub.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(int recipientId);
    int countByRecipientIdAndIsRead(int recipientId, boolean isRead);
    @Modifying @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :rid AND n.isRead = false")
    void markAllRead(int rid);
}
