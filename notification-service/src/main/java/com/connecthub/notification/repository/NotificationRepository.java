package com.connecthub.notification.repository;
import com.connecthub.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(int recipientId);
    int countByRecipientIdAndIsRead(int recipientId, boolean isRead);
    @Modifying @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :rid AND n.isRead = false")
    void markAllRead(int rid);
    /** P2-17: Delete notifications older than the retention threshold */
    @Modifying @Query("DELETE FROM Notification n WHERE n.createdAt < :threshold")
    long deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}

