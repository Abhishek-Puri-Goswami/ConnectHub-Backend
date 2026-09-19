package com.connecthub.message.service;

import com.connecthub.message.client.RoomClient;
import com.connecthub.message.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Room-level authorization for message operations, delegated to room-service (the owner of
 * membership). Fails closed: if room-service cannot answer, access is denied.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomAccess {

    private final RoomClient roomClient;

    public static boolean isPlatformAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "PLATFORM_ADMIN".equalsIgnoreCase(role);
    }

    public boolean isMember(String roomId, int uid) {
        if (roomId == null || roomId.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(roomClient.isMember(roomId, uid));
        } catch (Exception e) {
            log.warn("Room membership check failed room={} user={}: {}", roomId, uid, e.getMessage());
            return false;
        }
    }

    public void requireMember(String roomId, int uid) {
        if (!isMember(roomId, uid)) throw new ForbiddenException("You are not a member of this room");
    }

    /** Room ADMIN role (the room creator is stored as ADMIN too). */
    public void requireRoomAdmin(String roomId, int uid) {
        try {
            List<Map<String, Object>> members = roomClient.members(roomId, String.valueOf(uid));
            boolean admin = members != null && members.stream().anyMatch(m ->
                    m.get("userId") != null && String.valueOf(m.get("userId")).equals(String.valueOf(uid))
                            && "ADMIN".equals(m.get("role")));
            if (admin) return;
        } catch (Exception e) {
            log.warn("Room admin check failed room={} user={}: {}", roomId, uid, e.getMessage());
        }
        throw new ForbiddenException("Room admin access required");
    }
}
