package com.connecthub.message.repository;
import com.connecthub.message.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, String> {
    Optional<Message> findByMessageIdAndRoomId(String messageId, String roomId);
    // Cursor-based pagination including deleted (frontend renders placeholder for deleted)
    List<Message> findByRoomIdAndSentAtBeforeOrderBySentAtDesc(String roomId, LocalDateTime before, Pageable pageable);
    // First page including deleted
    List<Message> findByRoomIdOrderBySentAtDesc(String roomId, Pageable pageable);
    // Legacy filtered queries kept for unread counting only
    List<Message> findByRoomIdAndIsDeletedFalseAndSentAtBeforeOrderBySentAtDesc(String roomId, LocalDateTime before, Pageable pageable);
    List<Message> findByRoomIdAndIsDeletedFalseOrderBySentAtDesc(String roomId, Pageable pageable);
    long countByRoomIdAndSentAtAfterAndIsDeletedFalse(String roomId, LocalDateTime after);
    long countByRoomIdAndIsDeletedFalse(String roomId);
    @Query("SELECT m FROM Message m WHERE m.roomId = :rid AND m.isDeleted = false AND LOWER(m.content) LIKE LOWER(CONCAT('%',:kw,'%')) ORDER BY m.sentAt DESC")
    List<Message> searchInRoom(@Param("rid") String roomId, @Param("kw") String keyword);
    void deleteByRoomId(String roomId);
    void deleteBySenderId(int senderId);
}
