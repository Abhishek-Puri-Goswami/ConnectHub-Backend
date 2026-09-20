package com.connecthub.room.service;

import com.connecthub.room.dto.CreateRoomRequest;
import com.connecthub.room.entity.*;
import com.connecthub.room.exception.*;
import com.connecthub.room.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomRepository roomRepo;
    @Mock RoomMemberRepository memberRepo;
    @Mock RoomCacheService cacheService;
    @Mock UserDirectory users;
    @Mock org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks RoomService svc;

    // ── createRoom ───────────────────────────────────────────────────────────

    @Test
    void createRoom_verifiesMembersExist_andSavesNothingWhenTheyDoNot() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("GROUP");
        req.setName("Team");
        req.setMaxMembers(10);
        req.setMemberIds(java.util.List.of(2, 9999));
        doThrow(new BadRequestException("Unknown user id(s): [9999]")).when(users).requireExist(java.util.List.of(2, 9999), 1);

        assertThrows(BadRequestException.class, () -> svc.createRoom(1, req, "FREE"));

        verify(users).requireExist(java.util.List.of(2, 9999), 1);
        verifyNoInteractions(roomRepo);
    }

    @Test
    void createRoom_dmWithNonexistentUser_isRejected() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("DM");
        req.setMemberIds(java.util.List.of(424242));
        doThrow(new BadRequestException("Unknown user id(s): [424242]")).when(users).requireExist(java.util.List.of(424242), 1);

        assertThrows(BadRequestException.class, () -> svc.createRoom(1, req, "FREE"));
        verifyNoInteractions(roomRepo);
    }

    @Test
    void createGroup_success() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Dev Team"); req.setType("GROUP"); req.setMemberIds(List.of(2, 3));
        Room saved = Room.builder().roomId("r1").name("Dev Team").type("GROUP").createdById(1).maxMembers(500).build();
        when(roomRepo.save(any())).thenReturn(saved);
        when(roomRepo.countByCreatedByIdAndType(1, "GROUP")).thenReturn(0L);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Room result = svc.createRoom(1, req, "FREE");

        assertEquals("GROUP", result.getType());
        // creator + 2 members = 3 saves
        verify(memberRepo, times(3)).save(any());
    }

    @Test
    void createDM_success() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("DM"); req.setMemberIds(List.of(2));
        Room saved = Room.builder().roomId("dm1").type("DM").createdById(1).maxMembers(2).build();
        when(roomRepo.findDirectMessageRoom(1, 2)).thenReturn(Optional.empty());
        when(roomRepo.save(any())).thenReturn(saved);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("dm1")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Room result = svc.createRoom(1, req, "FREE");
        assertEquals("DM", result.getType());
    }

    @Test
    void createGroup_freeTierAtCap_throws() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Sixth"); req.setType("GROUP"); req.setMemberIds(List.of(2));
        when(roomRepo.countByCreatedByIdAndType(1, "GROUP")).thenReturn(5L);

        assertThrows(ForbiddenException.class, () -> svc.createRoom(1, req, "FREE"));
    }

    @Test
    void createGroup_proTierUnlimited_ok() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Another"); req.setType("GROUP"); req.setMemberIds(List.of(2));
        Room saved = Room.builder().roomId("r2").name("Another").type("GROUP").createdById(1).maxMembers(500).build();
        when(roomRepo.save(any())).thenReturn(saved);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("r2")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Room result = svc.createRoom(1, req, "PRO");
        assertEquals("GROUP", result.getType());
    }

    @Test
    void createGroup_premiumTierUnlimited_ok() {
        // PREMIUM was added alongside PLATINUM — must bypass the free-tier cap
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Premium Room"); req.setType("GROUP"); req.setMemberIds(List.of(2));
        Room saved = Room.builder().roomId("r3").name("Premium Room").type("GROUP").createdById(1).maxMembers(500).build();
        when(roomRepo.save(any())).thenReturn(saved);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("r3")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Room result = svc.createRoom(1, req, "PREMIUM");
        assertEquals("GROUP", result.getType());
        verify(roomRepo).countByCreatedByIdAndType(1, "GROUP"); // paid tiers are capped too (500)
    }

    @Test
    void createGroup_paidTier_isCappedAt500GroupChats_freeAt5() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("One too many"); req.setType("GROUP"); req.setMemberIds(List.of(2));
        when(roomRepo.countByCreatedByIdAndType(1, "GROUP")).thenReturn(500L);

        ForbiddenException paid = assertThrows(ForbiddenException.class, () -> svc.createRoom(1, req, "PREMIUM"));
        assertTrue(paid.getMessage().contains("500"), paid.getMessage());

        when(roomRepo.countByCreatedByIdAndType(1, "GROUP")).thenReturn(5L);
        ForbiddenException free = assertThrows(ForbiddenException.class, () -> svc.createRoom(1, req, "FREE"));
        assertTrue(free.getMessage().contains("5 group chats") && free.getMessage().contains("Pro"), free.getMessage());
        verify(roomRepo, never()).save(any());
    }

    @Test
    void createGroup_platinumTierUnlimited_ok() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Platinum Room"); req.setType("GROUP"); req.setMemberIds(List.of(2));
        Room saved = Room.builder().roomId("r4").name("Platinum Room").type("GROUP").createdById(1).maxMembers(500).build();
        when(roomRepo.save(any())).thenReturn(saved);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("r4")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Room result = svc.createRoom(1, req, "PLATINUM");
        assertEquals("GROUP", result.getType());
        verify(roomRepo).countByCreatedByIdAndType(1, "GROUP");
    }

    @Test
    void createGroup_businessTierUnlimited_ok() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Business Room"); req.setType("GROUP"); req.setMemberIds(List.of(2));
        Room saved = Room.builder().roomId("r5").name("Business Room").type("GROUP").createdById(1).maxMembers(500).build();
        when(roomRepo.save(any())).thenReturn(saved);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("r5")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Room result = svc.createRoom(1, req, "BUSINESS");
        assertEquals("GROUP", result.getType());
    }

    // ── members-per-room tier cap ───────────────────────────────────────────
    // Regression coverage: maxMembers used to be entirely requester-chosen with
    // no tier enforcement at all (any user could request up to 500 members on
    // the FREE plan). These prove the new cap is actually applied.

    @Test
    void createGroup_freeTierOverMemberCap_throws() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Too Big");
        req.setType("GROUP");
        // 25 other members + creator = 26 total, one over the FREE cap of 25
        req.setMemberIds(new ArrayList<>(java.util.stream.IntStream.rangeClosed(2, 26).boxed().toList()));
        when(roomRepo.countByCreatedByIdAndType(1, "GROUP")).thenReturn(0L);

        assertThrows(ForbiddenException.class, () -> svc.createRoom(1, req, "FREE"));
    }

    @Test
    void createGroup_freeTierAtMemberCap_ok() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Exactly At Cap");
        req.setType("GROUP");
        // 24 other members + creator = 25 total, exactly at the FREE cap
        req.setMemberIds(new ArrayList<>(java.util.stream.IntStream.rangeClosed(2, 25).boxed().toList()));
        Room saved = Room.builder().roomId("r6").name("Exactly At Cap").type("GROUP").createdById(1).maxMembers(25).build();
        when(roomRepo.countByCreatedByIdAndType(1, "GROUP")).thenReturn(0L);
        when(roomRepo.save(any())).thenReturn(saved);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("r6")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Room result = svc.createRoom(1, req, "FREE");
        assertEquals("GROUP", result.getType());
    }

    @Test
    void createGroup_freeTierRequestedMaxMembers_isCappedAt25() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Requests 500"); req.setType("GROUP"); req.setMemberIds(List.of(2));
        req.setMaxMembers(500); // requester asks for the field default — FREE tier must cap it
        Room saved = Room.builder().roomId("r7").name("Requests 500").type("GROUP").createdById(1).maxMembers(25).build();
        when(roomRepo.countByCreatedByIdAndType(1, "GROUP")).thenReturn(0L);
        when(roomRepo.save(any())).thenReturn(saved);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("r7")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.createRoom(1, req, "FREE");

        org.mockito.ArgumentCaptor<Room> captor = org.mockito.ArgumentCaptor.forClass(Room.class);
        verify(roomRepo).save(captor.capture());
        assertEquals(25, captor.getValue().getMaxMembers());
    }

    @Test
    void createGroup_premiumTierRequestedMaxMembers_isCappedAt250() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Requests 500"); req.setType("GROUP"); req.setMemberIds(List.of(2));
        req.setMaxMembers(500); // PREMIUM cap is 250 — must still be capped, just higher
        Room saved = Room.builder().roomId("r8").name("Requests 500").type("GROUP").createdById(1).maxMembers(250).build();
        when(roomRepo.save(any())).thenReturn(saved);
        when(memberRepo.existsByRoomIdAndUserId(any(), anyInt())).thenReturn(false);
        when(memberRepo.countByRoomId(any())).thenReturn(0);
        when(roomRepo.findByRoomId("r8")).thenReturn(Optional.of(saved));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.createRoom(1, req, "PREMIUM");

        org.mockito.ArgumentCaptor<Room> captor = org.mockito.ArgumentCaptor.forClass(Room.class);
        verify(roomRepo).save(captor.capture());
        assertEquals(250, captor.getValue().getMaxMembers());
    }

    @Test
    void createDM_wrongMemberCount_throws() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("DM"); req.setMemberIds(List.of(2, 3));
        assertThrows(BadRequestException.class, () -> svc.createRoom(1, req, "FREE"));
    }

    @Test
    void createDM_noMembers_throws() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("DM"); req.setMemberIds(null);
        assertThrows(BadRequestException.class, () -> svc.createRoom(1, req, "FREE"));
    }

    @Test
    void createGroup_withoutOtherMembers_throws() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Solo Group");
        req.setType("GROUP");
        req.setMemberIds(List.of());

        assertThrows(BadRequestException.class, () -> svc.createRoom(1, req, "FREE"));
    }

    @Test
    void createDM_returnsExistingRoomWhenPresent() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("dm");
        req.setMemberIds(List.of(2));
        Room existing = Room.builder().roomId("dm1").type("DM").createdById(1).maxMembers(2).build();

        when(roomRepo.findDirectMessageRoom(1, 2)).thenReturn(Optional.of(existing));

        Room result = svc.createRoom(1, req, "FREE");

        assertEquals("dm1", result.getRoomId());
        verify(roomRepo, never()).save(any());
        verify(memberRepo, never()).save(any());
    }

    // ── getRoom ──────────────────────────────────────────────────────────────

    @Test
    void getRoom_found() {
        Room room = Room.builder().roomId("r1").name("Test").build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        Optional<Room> result = svc.getRoom("r1");
        assertTrue(result.isPresent());
        assertEquals("Test", result.get().getName());
    }

    @Test
    void getRoom_notFound_returnsEmpty() {
        when(roomRepo.findByRoomId("missing")).thenReturn(Optional.empty());
        assertTrue(svc.getRoom("missing").isEmpty());
    }

    // ── getRoomsByUser ───────────────────────────────────────────────────────

    @Test
    void getRoomsByUser_returnsMappedRooms() {
        Room r = Room.builder().roomId("r1").build();
        when(memberRepo.findRoomIdsByUserId(1)).thenReturn(List.of("r1"));
        when(roomRepo.findAllById(List.of("r1"))).thenReturn(List.of(r));

        List<Room> result = svc.getRoomsByUser(1);
        assertEquals(1, result.size());
    }

    @Test
    void getRoomsByUser_noRooms_returnsEmpty() {
        when(memberRepo.findRoomIdsByUserId(99)).thenReturn(List.of());
        assertTrue(svc.getRoomsByUser(99).isEmpty());
    }

    // ── updateRoom ───────────────────────────────────────────────────────────

    @Test
    void updateRoom_changesName() {
        Room existing = Room.builder().roomId("r1").name("Old").build();
        Room updates = Room.builder().name("New").build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(existing));
        when(roomRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Room result = svc.updateRoom("r1", updates);
        assertEquals("New", result.getName());
    }

    @Test
    void updateRoom_notFound_throws() {
        when(roomRepo.findByRoomId("x")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> svc.updateRoom("x", new Room()));
    }

    // ── deleteRoom ───────────────────────────────────────────────────────────

    @Test
    void deleteRoom_removesAllMembers() {
        svc.deleteRoom("r1");
        verify(memberRepo).deleteByRoomId("r1");
        verify(roomRepo).deleteById("r1");
    }

    @Test
    void deleteRoom_publishesRoomDeleted_soOtherServicesRemoveTheRoomsData() {
        svc.deleteRoom("r1");
        verify(kafkaTemplate).send("room.deleted", "r1");
        verify(cacheService).evict("r1");
    }

    @Test
    void deleteRoom_publishFailureDoesNotUndoTheDelete() {
        when(kafkaTemplate.send(eq("room.deleted"), any())).thenThrow(new RuntimeException("kafka down"));
        assertDoesNotThrow(() -> svc.deleteRoom("r1"));
        verify(roomRepo).deleteById("r1");
    }

    // ── leaving / account deletion cleanup ───────────────────────────────────

    private RoomMember member(int uid, String role, int joinedDaysAgo) {
        return RoomMember.builder().roomId("g1").userId(uid).role(role)
                .joinedAt(java.time.LocalDateTime.now().minusDays(joinedDaysAgo)).build();
    }

    @Test
    void removeMember_lastMemberLeaves_roomIsDeleted() {
        Room room = Room.builder().roomId("g1").type("GROUP").createdById(1).build();
        when(roomRepo.findByRoomId("g1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomId("g1")).thenReturn(List.of());

        svc.removeMember("g1", 1);

        verify(roomRepo).deleteById("g1");
        verify(kafkaTemplate).send("room.deleted", "g1");
    }

    @Test
    void removeMember_creatorLeaves_roomGoesToTheLongestStandingAdmin() {
        Room room = Room.builder().roomId("g1").type("GROUP").createdById(1).build();
        RoomMember newer = member(3, "ADMIN", 1), older = member(2, "ADMIN", 9), plain = member(4, "MEMBER", 30);
        when(roomRepo.findByRoomId("g1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomId("g1")).thenReturn(List.of(newer, plain, older));

        svc.removeMember("g1", 1);

        assertEquals(2, room.getCreatedById());
        verify(roomRepo).save(room);
        verify(roomRepo, never()).deleteById(any());
    }

    @Test
    void removeMember_creatorLeaves_noAdmins_longestStandingMemberIsPromoted() {
        Room room = Room.builder().roomId("g1").type("GROUP").createdById(1).build();
        RoomMember a = member(5, "MEMBER", 2), b = member(6, "MEMBER", 20);
        when(roomRepo.findByRoomId("g1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomId("g1")).thenReturn(List.of(a, b));

        svc.removeMember("g1", 1);

        assertEquals(6, room.getCreatedById());
        assertEquals("ADMIN", b.getRole());
        verify(memberRepo).save(b);
    }

    @Test
    void removeMember_ordinaryMemberLeaves_creatorUnchanged() {
        Room room = Room.builder().roomId("g1").type("GROUP").createdById(1).build();
        when(roomRepo.findByRoomId("g1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomId("g1")).thenReturn(List.of(member(1, "ADMIN", 9)));

        svc.removeMember("g1", 7);

        assertEquals(1, room.getCreatedById());
        verify(roomRepo, never()).save(any());
    }

    @Test
    void onUserDeleted_dmIsDeleted_groupIsTidied_andLeftOverCreatedRoomsAreHandedOver() {
        Room dm = Room.builder().roomId("dm1").type("DM").createdById(9).build();
        Room group = Room.builder().roomId("g1").type("GROUP").createdById(42).build();
        Room leftButCreated = Room.builder().roomId("g2").type("GROUP").createdById(42).build();
        when(memberRepo.findRoomIdsByUserId(42)).thenReturn(List.of("dm1", "g1"));
        when(roomRepo.findByRoomId("dm1")).thenReturn(Optional.of(dm));
        when(roomRepo.findByRoomId("g1")).thenReturn(Optional.of(group));
        when(roomRepo.findByRoomId("g2")).thenReturn(Optional.of(leftButCreated));
        when(memberRepo.findByRoomId("g1")).thenReturn(List.of(member(7, "ADMIN", 3)));
        when(memberRepo.findByRoomId("g2")).thenReturn(List.of());
        when(roomRepo.findByCreatedById(42)).thenReturn(List.of(group, leftButCreated));

        svc.onUserDeleted(42);

        verify(memberRepo).deleteByRoomIdAndUserId("dm1", 42);
        verify(roomRepo).deleteById("dm1");                    // DM with a deleted person is deleted
        verify(kafkaTemplate).send("room.deleted", "dm1");
        assertEquals(7, group.getCreatedById());                // group handed over
        verify(roomRepo).deleteById("g2");                     // orphan room nobody is in is deleted
        verify(kafkaTemplate).send("room.deleted", "g2");
    }

    // ── addMember ────────────────────────────────────────────────────────────

    @Test
    void addMember_success() {
        Room room = Room.builder().roomId("r1").maxMembers(500).build();
        when(memberRepo.existsByRoomIdAndUserId("r1", 5)).thenReturn(false);
        when(memberRepo.countByRoomId("r1")).thenReturn(10);
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        RoomMember m = svc.addMember("r1", 5, "MEMBER");
        assertEquals(5, m.getUserId());
        assertEquals("MEMBER", m.getRole());
    }

    @Test
    void addMember_alreadyMember_throws() {
        when(memberRepo.existsByRoomIdAndUserId("r1", 5)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> svc.addMember("r1", 5, "MEMBER"));
    }

    @Test
    void addMember_roomFull_throws() {
        when(memberRepo.existsByRoomIdAndUserId("r1", 5)).thenReturn(false);
        when(memberRepo.countByRoomId("r1")).thenReturn(500);
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(Room.builder().roomId("r1").maxMembers(500).build()));
        assertThrows(BadRequestException.class, () -> svc.addMember("r1", 5, "MEMBER"));
    }

    // ── removeMember ─────────────────────────────────────────────────────────

    @Test
    void removeMember_callsRepository() {
        svc.removeMember("r1", 5);
        verify(memberRepo).deleteByRoomIdAndUserId("r1", 5);
    }

    // ── getMembers ───────────────────────────────────────────────────────────

    @Test
    void getMembers_returnsList() {
        List<RoomMember> members = List.of(
            RoomMember.builder().roomId("r1").userId(1).role("ADMIN").build(),
            RoomMember.builder().roomId("r1").userId(2).role("MEMBER").build()
        );
        when(cacheService.getCachedMembers("r1")).thenReturn(null); // cache miss
        when(memberRepo.findByRoomId("r1")).thenReturn(members);
        assertEquals(2, svc.getMembers("r1").size());
    }

    @Test
    void getMembers_cacheHit_returnsCachedList() {
        List<RoomMember> cached = List.of(
            RoomMember.builder().roomId("r1").userId(1).role("ADMIN").build()
        );
        when(cacheService.getCachedMembers("r1")).thenReturn(cached); // cache hit

        List<RoomMember> result = svc.getMembers("r1");

        assertEquals(1, result.size());
        // DB must never be queried on a cache hit
        verify(memberRepo, never()).findByRoomId(any());
    }

    // ── updateRole ───────────────────────────────────────────────────────────

    @Test
    void updateRole_changesToModerator() {
        RoomMember m = RoomMember.builder().roomId("r1").userId(2).role("MEMBER").build();
        when(memberRepo.findByRoomIdAndUserId("r1", 2)).thenReturn(Optional.of(m));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.updateRole("r1", 2, "MODERATOR");
        assertEquals("MODERATOR", m.getRole());
    }

    @Test
    void updateRole_memberNotFound_throws() {
        when(memberRepo.findByRoomIdAndUserId("r1", 99)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> svc.updateRole("r1", 99, "ADMIN"));
    }

    // ── mute ─────────────────────────────────────────────────────────────────

    @Test
    void mute_setsMutedTrue() {
        RoomMember m = RoomMember.builder().roomId("r1").userId(2).isMuted(false).build();
        when(memberRepo.findByRoomIdAndUserId("r1", 2)).thenReturn(Optional.of(m));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.mute("r1", 2, true);
        assertTrue(m.isMuted());
    }

    @Test
    void unmute_setsMutedFalse() {
        RoomMember m = RoomMember.builder().roomId("r1").userId(2).isMuted(true).build();
        when(memberRepo.findByRoomIdAndUserId("r1", 2)).thenReturn(Optional.of(m));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.mute("r1", 2, false);
        assertFalse(m.isMuted());
    }

    // ── pinMessage ───────────────────────────────────────────────────────────

    @Test
    void pinMessage_setsPinnedMessageId() {
        Room room = Room.builder().roomId("r1").build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        when(roomRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.pinMessage("r1", "m42");
        assertEquals("m42", room.getPinnedMessageId());
    }

    // ── isMember ─────────────────────────────────────────────────────────────

    @Test
    void isMember_trueWhenExists() {
        when(memberRepo.existsByRoomIdAndUserId("r1", 1)).thenReturn(true);
        assertTrue(svc.isMember("r1", 1));
    }

    @Test
    void isMember_falseWhenNotExists() {
        when(memberRepo.existsByRoomIdAndUserId("r1", 99)).thenReturn(false);
        assertFalse(svc.isMember("r1", 99));
    }

    // ── updateLastRead ────────────────────────────────────────────────────────

    @Test
    void updateLastRead_memberFound_setsTimestamp() {
        RoomMember member = RoomMember.builder().roomId("r1").userId(1).build();
        when(memberRepo.findByRoomIdAndUserId("r1", 1)).thenReturn(Optional.of(member));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.updateLastRead("r1", 1);

        assertNotNull(member.getLastReadAt());
        verify(memberRepo).save(member);
    }

    @Test
    void updateLastRead_memberNotFound_noOp() {
        when(memberRepo.findByRoomIdAndUserId("r1", 99)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> svc.updateLastRead("r1", 99));
        verify(memberRepo, never()).save(any());
    }

    // ── updateLastMessageAt ───────────────────────────────────────────────────

    @Test
    void updateLastMessageAt_roomFound_updatesPreviewAndSender() {
        Room room = Room.builder().roomId("r1").build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        when(roomRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.updateLastMessageAt("r1", "Hello!", 42);

        assertNotNull(room.getLastMessageAt());
        assertEquals("Hello!", room.getLastMessagePreview());
        assertEquals(42, room.getLastMessageSenderId());
    }

    @Test
    void updateLastMessageAt_previewTruncatedAt200() {
        Room room = Room.builder().roomId("r1").build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        when(roomRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        String longPreview = "a".repeat(250);

        svc.updateLastMessageAt("r1", longPreview, 1);

        assertEquals(200, room.getLastMessagePreview().length());
    }

    // ── getAllRooms / getAllRoomsPaged / countActiveRooms ──────────────────────

    @Test
    void getAllRooms_returnsList() {
        List<Room> rooms = List.of(Room.builder().roomId("r1").build());
        when(roomRepo.findAll()).thenReturn(rooms);

        assertEquals(1, svc.getAllRooms().size());
    }

    @Test
    void countActiveRooms_delegatesToRepo() {
        when(roomRepo.countActiveRooms()).thenReturn(7L);
        assertEquals(7L, svc.countActiveRooms());
    }

    // ── searchRooms ───────────────────────────────────────────────────────────

    @Test
    void searchRooms_blankQuery_returnsEmpty() {
        assertTrue(svc.searchRooms("   ").isEmpty());
        verify(roomRepo, never()).searchPublicRooms(any());
    }

    @Test
    void searchRooms_nullQuery_returnsEmpty() {
        assertTrue(svc.searchRooms(null).isEmpty());
    }

    @Test
    void searchRooms_validQuery_delegatesToRepo() {
        List<Room> results = List.of(Room.builder().roomId("r1").name("Java Chat").build());
        when(roomRepo.searchPublicRooms("java")).thenReturn(results);

        assertEquals(1, svc.searchRooms("java").size());
    }

    // ── generateInviteCode ────────────────────────────────────────────────────

    @Test
    void generateInviteCode_success_returnsCode() {
        Room room = Room.builder().roomId("r1").type("GROUP").createdById(1).build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        when(roomRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String code = svc.generateInviteCode("r1", 1);

        assertNotNull(code);
        assertEquals(8, code.length());
        assertEquals(code, room.getInviteCode());
    }

    @Test
    void generateInviteCode_notCreator_throws403() {
        Room room = Room.builder().roomId("r1").type("GROUP").createdById(1).build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));

        assertThrows(ForbiddenException.class, () -> svc.generateInviteCode("r1", 99));
    }

    @Test
    void generateInviteCode_dmRoom_throws400() {
        Room room = Room.builder().roomId("r1").type("DM").createdById(1).build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));

        assertThrows(BadRequestException.class, () -> svc.generateInviteCode("r1", 1));
    }

    // ── joinByInviteCode ──────────────────────────────────────────────────────

    @Test
    void joinByInviteCode_newMember_addsAndReturns() {
        Room room = Room.builder().roomId("r1").type("GROUP").createdById(1)
                .name("Room").maxMembers(10).build();
        when(roomRepo.findByInviteCode("CODE123")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId("r1", 5)).thenReturn(false);
        // addMember internally calls roomRepo.findByRoomId again
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        when(memberRepo.countByRoomId("r1")).thenReturn(1);
        RoomMember saved = RoomMember.builder().roomId("r1").userId(5).role("MEMBER").build();
        when(memberRepo.save(any())).thenReturn(saved);

        RoomMember result = svc.joinByInviteCode("CODE123", 5);

        assertEquals(5, result.getUserId());
    }

    @Test
    void joinByInviteCode_alreadyMember_returnsExisting() {
        Room room = Room.builder().roomId("r1").build();
        when(roomRepo.findByInviteCode("CODE123")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId("r1", 5)).thenReturn(true);
        RoomMember existing = RoomMember.builder().roomId("r1").userId(5).build();
        when(memberRepo.findByRoomIdAndUserId("r1", 5)).thenReturn(Optional.of(existing));

        RoomMember result = svc.joinByInviteCode("CODE123", 5);

        assertEquals(5, result.getUserId());
        verify(memberRepo, never()).save(any());
    }

    @Test
    void joinByInviteCode_invalidCode_throws() {
        when(roomRepo.findByInviteCode("INVALID")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> svc.joinByInviteCode("INVALID", 5));
    }

    // ── revokeInviteCode ──────────────────────────────────────────────────────

    @Test
    void revokeInviteCode_byCreator_clearsCode() {
        Room room = Room.builder().roomId("r1").createdById(1).inviteCode("CODE123").build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));
        when(roomRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        svc.revokeInviteCode("r1", 1);

        assertNull(room.getInviteCode());
    }

    @Test
    void revokeInviteCode_notCreator_throws403() {
        Room room = Room.builder().roomId("r1").createdById(1).build();
        when(roomRepo.findByRoomId("r1")).thenReturn(Optional.of(room));

        assertThrows(ForbiddenException.class, () -> svc.revokeInviteCode("r1", 99));
    }
}
