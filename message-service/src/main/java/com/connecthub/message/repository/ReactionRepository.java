package com.connecthub.message.repository;
import com.connecthub.message.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReactionRepository extends JpaRepository<MessageReaction, Long> {
    List<MessageReaction> findByMessageId(String messageId);
    boolean existsByMessageIdAndUserIdAndEmoji(String messageId, int userId, String emoji);
    void deleteByMessageIdAndUserIdAndEmoji(String messageId, int userId, String emoji);
    void deleteByUserId(int userId);

    /** Reactions on any message of the room (used before the room's messages are deleted). */
    @Modifying
    @Query("DELETE FROM MessageReaction r WHERE r.messageId IN (SELECT m.messageId FROM Message m WHERE m.roomId = :roomId)")
    void deleteByRoomId(@Param("roomId") String roomId);

    /** Reactions other people left on this user's messages (used before the user's messages are deleted). */
    @Modifying
    @Query("DELETE FROM MessageReaction r WHERE r.messageId IN (SELECT m.messageId FROM Message m WHERE m.senderId = :senderId)")
    void deleteOnMessagesBySender(@Param("senderId") int senderId);
}
