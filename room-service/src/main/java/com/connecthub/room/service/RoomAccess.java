package com.connecthub.room.service;

import com.connecthub.room.entity.Room;
import com.connecthub.room.entity.RoomMember;
import com.connecthub.room.exception.ForbiddenException;
import com.connecthub.room.repository.RoomMemberRepository;
import com.connecthub.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Authorization rules for room operations. The caller identity (X-User-Id / X-User-Role) is
 * injected by the api-gateway from the verified JWT, so it is trusted here.
 * Every require* check throws {@link ForbiddenException} (HTTP 403) on failure.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomAccess {

    public static final String ROLE_ADMIN = "ADMIN";

    private final RoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;

    public static boolean isPlatformAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "PLATFORM_ADMIN".equalsIgnoreCase(role);
    }

    public static boolean isValidMemberRole(String role) {
        return "MEMBER".equals(role) || ROLE_ADMIN.equals(role);
    }

    public boolean isMember(String roomId, int uid) {
        return memberRepo.existsByRoomIdAndUserId(roomId, uid);
    }

    public void requireMember(String roomId, int uid) {
        if (!isMember(roomId, uid)) throw new ForbiddenException("You are not a member of this room");
    }

    /** Room creator, or a member holding the ADMIN role. */
    public boolean isRoomAdmin(String roomId, int uid) {
        Optional<Room> room = roomRepo.findByRoomId(roomId);
        if (room.isPresent() && isCreator(room.get(), uid)) return true;
        return memberRepo.findByRoomIdAndUserId(roomId, uid)
                .map(RoomMember::getRole).filter(ROLE_ADMIN::equals).isPresent();
    }

    public void requireRoomAdmin(String roomId, int uid) {
        if (!isRoomAdmin(roomId, uid)) throw new ForbiddenException("Room admin access required");
    }

    public void requireCreatorOrPlatformAdmin(String roomId, int uid, String role) {
        if (isPlatformAdmin(role)) return;
        boolean creator = roomRepo.findByRoomId(roomId).map(r -> isCreator(r, uid)).orElse(false);
        if (!creator) throw new ForbiddenException("Only the room creator can do this");
    }

    public boolean isCreator(Room room, int uid) {
        return room.getCreatedById() != null && room.getCreatedById() == uid;
    }

    /**
     * Detached copy that is safe to return to a caller: the invite code is included only for
     * the room creator (a code lets anyone join, so it is a secret).
     */
    public Room sanitize(Room r, int uid) {
        return Room.builder().roomId(r.getRoomId()).name(r.getName()).description(r.getDescription())
                .type(r.getType()).createdById(r.getCreatedById()).avatarUrl(r.getAvatarUrl())
                .isPrivate(r.isPrivate()).maxMembers(r.getMaxMembers()).lastMessageAt(r.getLastMessageAt())
                .lastMessagePreview(r.getLastMessagePreview()).lastMessageSenderId(r.getLastMessageSenderId())
                .pinnedMessageId(r.getPinnedMessageId())
                .inviteCode(isCreator(r, uid) ? r.getInviteCode() : null)
                .createdAt(r.getCreatedAt()).memberCount(r.getMemberCount()).build();
    }

    /** A non-member may only see public group rooms (room discovery). */
    public boolean canView(Room r, int uid) {
        return isMember(r.getRoomId(), uid) || (!r.isPrivate() && "GROUP".equals(r.getType()));
    }
}
