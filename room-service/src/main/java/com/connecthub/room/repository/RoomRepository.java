package com.connecthub.room.repository;
import com.connecthub.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, String> {
    Optional<Room> findByRoomId(String roomId);

    @Query("""
        SELECT r
        FROM Room r
        WHERE r.type = 'DM'
          AND EXISTS (
              SELECT 1 FROM RoomMember rm1
              WHERE rm1.roomId = r.roomId AND rm1.userId = :firstUserId
          )
          AND EXISTS (
              SELECT 1 FROM RoomMember rm2
              WHERE rm2.roomId = r.roomId AND rm2.userId = :secondUserId
          )
          AND (
              SELECT COUNT(rm3)
              FROM RoomMember rm3
              WHERE rm3.roomId = r.roomId
          ) = 2
        """)
    Optional<Room> findDirectMessageRoom(@Param("firstUserId") int firstUserId, @Param("secondUserId") int secondUserId);

    long countByCreatedByIdAndType(int createdById, String type);

    /** P2-14: Search public rooms by name or description keyword */
    @Query("SELECT r FROM Room r WHERE r.isPrivate = false AND r.type = 'GROUP' " +
           "AND (LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(r.description) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Room> searchPublicRooms(@Param("q") String query);

    /** P2-13: Find room by invite code */
    Optional<Room> findByInviteCode(String inviteCode);

    /** Returns the number of rooms that have received at least one message (lastMessageAt set). */
    @Query("SELECT COUNT(r) FROM Room r WHERE r.lastMessageAt IS NOT NULL")
    long countActiveRooms();
}

