package com.connecthub.room.resource;

import com.connecthub.room.dto.CreateRoomRequest;
import com.connecthub.room.entity.Room;
import com.connecthub.room.entity.RoomMember;
import com.connecthub.room.exception.BadRequestException;
import com.connecthub.room.exception.ForbiddenException;
import com.connecthub.room.repository.RoomMemberRepository;
import com.connecthub.room.repository.RoomRepository;
import com.connecthub.room.service.RoomAccess;
import com.connecthub.room.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Authorization tests. Fixture: room "r1" (GROUP, private) created by user 1; user 2 is a plain
 * MEMBER, user 3 is an ADMIN member, user 9 is not in the room at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomResourceTest {

    @Mock private RoomService svc;
    @Mock private RoomRepository roomRepo;
    @Mock private RoomMemberRepository memberRepo;

    private RoomResource res;
    private Room room;

    @BeforeEach
    void setUp() {
        res = new RoomResource(svc, new RoomAccess(roomRepo, memberRepo));
        room = Room.builder().roomId("r1").type("GROUP").createdById(1).isPrivate(true).inviteCode("SECRET01").build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        when(svc.getRoom("r1")).thenReturn(Optional.of(room));
        member(1, "ADMIN");
        member(2, "MEMBER");
        member(3, "ADMIN");
    }

    private void member(int uid, String role) {
        when(memberRepo.existsByRoomIdAndUserId("r1", uid)).thenReturn(true);
        when(memberRepo.findByRoomIdAndUserId("r1", uid))
                .thenReturn(Optional.of(RoomMember.builder().roomId("r1").userId(uid).role(role).build()));
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create() {
        CreateRoomRequest req = new CreateRoomRequest();
        when(svc.createRoom(1, req, "PRO")).thenReturn(new Room());
        assertEquals(HttpStatus.CREATED, res.create(1, "PRO", req).getStatusCode());
    }

    // ── get ──────────────────────────────────────────────────────────────────

    @Test
    void get_memberSeesRoom_butInviteCodeOnlyForCreator() {
        assertNull(res.get("r1", 2).getBody().getInviteCode());
        assertEquals("SECRET01", res.get("r1", 1).getBody().getInviteCode());
    }

    @Test
    void get_nonMemberOfPrivateRoom_forbidden() {
        assertEquals(HttpStatus.FORBIDDEN, res.get("r1", 9).getStatusCode());
    }

    @Test
    void get_nonMemberOfPublicGroup_allowedWithoutInviteCode() {
        room.setPrivate(false);
        var r = res.get("r1", 9);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertNull(r.getBody().getInviteCode());
    }

    @Test
    void get_notFound() {
        when(svc.getRoom("nope")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, res.get("nope", 1).getStatusCode());
    }

    // ── byUser ───────────────────────────────────────────────────────────────

    @Test
    void byUser_self_ok() {
        when(svc.getRoomsByUser(2)).thenReturn(List.of(room));
        assertEquals(HttpStatus.OK, res.byUser(2, 2, "USER").getStatusCode());
    }

    @Test
    void byUser_otherUser_forbidden() {
        assertThrows(ForbiddenException.class, () -> res.byUser(2, 9, "USER"));
    }

    @Test
    void byUser_platformAdmin_ok() {
        when(svc.getRoomsByUser(2)).thenReturn(List.of(room));
        assertEquals(HttpStatus.OK, res.byUser(2, 9, "PLATFORM_ADMIN").getStatusCode());
    }

    // ── update / delete ──────────────────────────────────────────────────────

    @Test
    void update_roomAdmin_ok() {
        Room upd = new Room();
        when(svc.updateRoom("r1", upd)).thenReturn(room);
        assertEquals(HttpStatus.OK, res.update("r1", upd, 3).getStatusCode());
    }

    @Test
    void update_plainMemberOrOutsider_forbidden() {
        assertThrows(ForbiddenException.class, () -> res.update("r1", new Room(), 2));
        assertThrows(ForbiddenException.class, () -> res.update("r1", new Room(), 9));
        verify(svc, never()).updateRoom(any(), any());
    }

    @Test
    void delete_creator_ok() {
        assertEquals(HttpStatus.NO_CONTENT, res.delete("r1", 1, "USER").getStatusCode());
        verify(svc).deleteRoom("r1");
    }

    @Test
    void delete_nonCreator_forbidden_evenIfRoomAdmin() {
        assertThrows(ForbiddenException.class, () -> res.delete("r1", 3, "USER"));
        assertThrows(ForbiddenException.class, () -> res.delete("r1", 9, "USER"));
        verify(svc, never()).deleteRoom(any());
    }

    @Test
    void delete_platformAdmin_ok() {
        assertEquals(HttpStatus.NO_CONTENT, res.delete("r1", 9, "PLATFORM_ADMIN").getStatusCode());
    }

    // ── members ──────────────────────────────────────────────────────────────

    @Test
    void addMember_outsiderCannotAddThemselvesAsAdmin() {
        assertThrows(ForbiddenException.class, () -> res.addMember("r1", 9, "ADMIN", 9));
        verify(svc, never()).addMember(any(), anyInt(), any());
    }

    @Test
    void addMember_roomAdminAddsMember_ok() {
        when(svc.addMember("r1", 9, "MEMBER")).thenReturn(new RoomMember());
        assertEquals(HttpStatus.CREATED, res.addMember("r1", 9, "MEMBER", 3).getStatusCode());
    }

    @Test
    void addMember_nonCreatorCannotGrantAdmin() {
        assertThrows(ForbiddenException.class, () -> res.addMember("r1", 9, "ADMIN", 3));
    }

    @Test
    void addMember_creatorCanGrantAdmin_andInvalidRoleRejected() {
        when(svc.addMember("r1", 9, "ADMIN")).thenReturn(new RoomMember());
        assertEquals(HttpStatus.CREATED, res.addMember("r1", 9, "ADMIN", 1).getStatusCode());
        assertThrows(BadRequestException.class, () -> res.addMember("r1", 9, "OWNER", 1));
    }

    @Test
    void addMember_toDm_rejected() {
        room.setType("DM");
        assertThrows(BadRequestException.class, () -> res.addMember("r1", 9, "MEMBER", 1));
    }

    @Test
    void removeMember_selfLeave_ok() {
        assertEquals(HttpStatus.NO_CONTENT, res.removeMember("r1", 2, 2, "USER").getStatusCode());
        verify(svc).removeMember("r1", 2);
    }

    @Test
    void removeMember_memberKickingOther_forbidden() {
        assertThrows(ForbiddenException.class, () -> res.removeMember("r1", 3, 2, "USER"));
    }

    @Test
    void removeMember_adminCannotKickCreator() {
        assertThrows(ForbiddenException.class, () -> res.removeMember("r1", 1, 3, "USER"));
        verify(svc, never()).removeMember(any(), anyInt());
    }

    @Test
    void removeMember_adminKicksMember_ok() {
        assertEquals(HttpStatus.NO_CONTENT, res.removeMember("r1", 2, 3, "USER").getStatusCode());
    }

    @Test
    void members_memberOk_outsiderForbidden() {
        when(svc.getMembers("r1")).thenReturn(List.of(new RoomMember()));
        assertEquals(HttpStatus.OK, res.members("r1", 2).getStatusCode());
        assertThrows(ForbiddenException.class, () -> res.members("r1", 9));
    }

    @Test
    void role_adminCanChangeMember_butNeverTheCreator() {
        assertEquals(HttpStatus.NO_CONTENT, res.role("r1", 2, Map.of("role", "ADMIN"), 3).getStatusCode());
        verify(svc).updateRole("r1", 2, "ADMIN");
        assertThrows(ForbiddenException.class, () -> res.role("r1", 1, Map.of("role", "MEMBER"), 3));
        verify(svc, never()).updateRole("r1", 1, "MEMBER");
    }

    @Test
    void role_outsiderAndPlainMemberForbidden_invalidRoleRejected() {
        assertThrows(ForbiddenException.class, () -> res.role("r1", 2, Map.of("role", "ADMIN"), 9));
        assertThrows(ForbiddenException.class, () -> res.role("r1", 2, Map.of("role", "ADMIN"), 2));
        assertThrows(BadRequestException.class, () -> res.role("r1", 2, Map.of("role", "GOD"), 1));
    }

    @Test
    void mute_selfOk_otherNeedsAdmin() {
        assertEquals(HttpStatus.NO_CONTENT, res.mute("r1", 2, true, 2).getStatusCode());
        assertThrows(ForbiddenException.class, () -> res.mute("r1", 3, true, 2));
        assertEquals(HttpStatus.NO_CONTENT, res.mute("r1", 2, true, 3).getStatusCode());
    }

    @Test
    void read_onlyForSelf() {
        assertEquals(HttpStatus.NO_CONTENT, res.read("r1", 2, 2).getStatusCode());
        verify(svc).updateLastRead("r1", 2);
        assertThrows(ForbiddenException.class, () -> res.read("r1", 2, 9));
    }

    @Test
    void pin_unpin_membersOnly() {
        assertEquals(HttpStatus.NO_CONTENT, res.pin("r1", "m1", 2).getStatusCode());
        verify(svc).pinMessage("r1", "m1");
        assertEquals(HttpStatus.NO_CONTENT, res.unpin("r1", 2).getStatusCode());
        verify(svc).pinMessage("r1", null);
        assertThrows(ForbiddenException.class, () -> res.pin("r1", "m1", 9));
        assertThrows(ForbiddenException.class, () -> res.unpin("r1", 9));
    }

    // ── check (internal) ─────────────────────────────────────────────────────

    @Test
    void check_internalCallerMayAskAboutAnyone() {
        when(svc.isMember("r1", 2)).thenReturn(true);
        assertEquals(HttpStatus.OK, res.check("r1", 2, null, "media-service").getStatusCode());
    }

    @Test
    void check_userMayOnlyAskAboutSelf() {
        when(svc.isMember("r1", 2)).thenReturn(true);
        assertEquals(HttpStatus.OK, res.check("r1", 2, 2, null).getStatusCode());
        assertThrows(ForbiddenException.class, () -> res.check("r1", 2, 9, null));
        assertThrows(ForbiddenException.class, () -> res.check("r1", 2, null, null));
    }

    // ── admin-only / internal-only ───────────────────────────────────────────

    @Test
    void all_platformAdminOnly() {
        when(svc.getAllRooms()).thenReturn(List.of(room));
        assertEquals(HttpStatus.OK, res.all(null, null, 9, "PLATFORM_ADMIN").getStatusCode());
        assertThrows(ForbiddenException.class, () -> res.all(null, null, 2, "USER"));
    }

    @Test
    void all_neverLeaksInviteCodes() {
        when(svc.getAllRooms()).thenReturn(List.of(room));
        @SuppressWarnings("unchecked")
        List<Room> body = (List<Room>) res.all(null, null, 9, "PLATFORM_ADMIN").getBody();
        assertNull(body.get(0).getInviteCode());
    }

    @Test
    void updateTimestamp_internalOnly() {
        Map<String, Object> body = Map.of("preview", "hello", "senderId", 1);
        assertEquals(HttpStatus.NO_CONTENT, res.updateTimestamp("r1", body, "websocket-service").getStatusCode());
        verify(svc).updateLastMessageAt(eq("r1"), any(), any());
        assertThrows(ForbiddenException.class, () -> res.updateTimestamp("r1", body, null));
    }

    @Test
    void search_hidesInviteCodes() {
        room.setPrivate(false);
        when(svc.searchRooms("x")).thenReturn(List.of(room));
        assertNull(res.search("x", 9).getBody().get(0).getInviteCode());
    }
}
