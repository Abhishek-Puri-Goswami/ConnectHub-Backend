package com.connecthub.room.resource;

import com.connecthub.room.dto.CreateRoomRequest;
import com.connecthub.room.entity.*;
import com.connecthub.room.exception.BadRequestException;
import com.connecthub.room.exception.ForbiddenException;
import com.connecthub.room.exception.ResourceNotFoundException;
import com.connecthub.room.service.RoomAccess;
import com.connecthub.room.service.InviteLookupLimiter;
import com.connecthub.room.service.RoomService;
import com.connecthub.room.service.UserDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Every endpoint authorizes the caller (X-User-Id / X-User-Role, injected by the gateway from the
 * verified JWT) before touching data — see {@link RoomAccess} for the rules. Endpoints marked
 * internal are for other services, which identify themselves with X-Internal-Service (the gateway
 * strips that header from external requests).
 */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room and channel management")
@SuppressWarnings("java:S4684")
public class RoomResource {
	private final RoomService svc;
	private final RoomAccess access;
	private final UserDirectory users;
	private final InviteLookupLimiter inviteLimiter;

	@PostMapping
	@Operation(summary = "Create room (GROUP or DM)")
	public ResponseEntity<Room> create(
			@RequestHeader("X-User-Id") int uid,
			@RequestHeader(value = "X-Subscription-Tier", required = false) String subscriptionTier,
			@Valid @RequestBody CreateRoomRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(svc.createRoom(uid, req, subscriptionTier));
	}

	@GetMapping("/{id}")
	public ResponseEntity<Room> get(@PathVariable String id, @RequestHeader("X-User-Id") int uid) {
		Optional<Room> found = svc.getRoom(id);
		if (found.isEmpty()) return ResponseEntity.notFound().build();
		if (!access.canView(found.get(), uid)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		return ResponseEntity.ok(access.sanitize(found.get(), uid));
	}

	@GetMapping("/user/{uid}")
	public ResponseEntity<List<Room>> byUser(@PathVariable int uid, @RequestHeader("X-User-Id") int caller,
			@RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
		if (uid != caller && !RoomAccess.isPlatformAdmin(role)) throw new ForbiddenException("Not allowed");
		return ResponseEntity.ok(svc.getRoomsByUser(uid).stream().map(r -> access.sanitize(r, caller)).toList());
	}

	@PutMapping("/{id}")
	public ResponseEntity<Room> update(@PathVariable String id, @RequestBody Room r,
			@RequestHeader("X-User-Id") int uid) {
		access.requireRoomAdmin(id, uid);
		return ResponseEntity.ok(access.sanitize(svc.updateRoom(id, r), uid));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id, @RequestHeader("X-User-Id") int uid,
			@RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
		access.requireCreatorOrPlatformAdmin(id, uid, role);
		svc.deleteRoom(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/members/{uid}")
	public ResponseEntity<RoomMember> addMember(@PathVariable String id, @PathVariable int uid,
			@RequestParam(defaultValue = "MEMBER") String role, @RequestHeader("X-User-Id") int caller) {
		access.requireRoomAdmin(id, caller);
		if (!RoomAccess.isValidMemberRole(role)) throw new BadRequestException("Invalid role");
		Room room = svc.getRoom(id).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
		if ("DM".equals(room.getType())) throw new BadRequestException("Cannot add members to a DM");
		users.requireExist(java.util.List.of(uid), caller);
		// Only the creator may hand out the ADMIN role
		if (RoomAccess.ROLE_ADMIN.equals(role) && !access.isCreator(room, caller))
			throw new ForbiddenException("Only the room creator can add admins");
		return ResponseEntity.status(HttpStatus.CREATED).body(svc.addMember(id, uid, role));
	}

	@DeleteMapping("/{id}/members/{uid}")
	public ResponseEntity<Void> removeMember(@PathVariable String id, @PathVariable int uid,
			@RequestHeader("X-User-Id") int caller,
			@RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
		boolean self = uid == caller;
		if (!self && !RoomAccess.isPlatformAdmin(role)) {
			access.requireRoomAdmin(id, caller);
			Room room = svc.getRoom(id).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
			if (access.isCreator(room, uid)) throw new ForbiddenException("The room creator cannot be removed");
		}
		svc.removeMember(id, uid);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/members")
	public ResponseEntity<List<RoomMember>> members(@PathVariable String id, @RequestHeader("X-User-Id") int uid) {
		access.requireMember(id, uid);
		return ResponseEntity.ok(svc.getMembers(id));
	}

	@PutMapping("/{id}/members/{uid}/role")
	public ResponseEntity<Void> role(@PathVariable String id, @PathVariable int uid,
			@RequestBody Map<String, String> b, @RequestHeader("X-User-Id") int caller) {
		access.requireRoomAdmin(id, caller);
		String newRole = b.get("role");
		if (!RoomAccess.isValidMemberRole(newRole)) throw new BadRequestException("Invalid role");
		Room room = svc.getRoom(id).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
		if (access.isCreator(room, uid)) throw new ForbiddenException("The room creator's role cannot be changed");
		svc.updateRole(id, uid, newRole);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/members/{uid}/mute")
	public ResponseEntity<Void> mute(@PathVariable String id, @PathVariable int uid, @RequestParam boolean muted,
			@RequestHeader("X-User-Id") int caller) {
		if (uid != caller) access.requireRoomAdmin(id, caller);
		svc.mute(id, uid, muted);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/read/{uid}")
	public ResponseEntity<Void> read(@PathVariable String id, @PathVariable int uid,
			@RequestHeader("X-User-Id") int caller) {
		if (uid != caller) throw new ForbiddenException("Not allowed");
		svc.updateLastRead(id, uid);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/pin/{msgId}")
	public ResponseEntity<Void> pin(@PathVariable String id, @PathVariable String msgId,
			@RequestHeader("X-User-Id") int caller) {
		access.requireMember(id, caller);
		svc.pinMessage(id, msgId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}/pin")
	public ResponseEntity<Void> unpin(@PathVariable String id, @RequestHeader("X-User-Id") int caller) {
		access.requireMember(id, caller);
		svc.pinMessage(id, null);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Membership check. Other services call this (internal); an end user may only ask about themselves.
	 */
	@GetMapping("/{id}/members/{uid}/check")
	public ResponseEntity<Boolean> check(@PathVariable String id, @PathVariable int uid,
			@RequestHeader(value = "X-User-Id", required = false) Integer caller,
			@RequestHeader(value = "X-Internal-Service", required = false) String internal) {
		boolean isInternal = internal != null && !internal.isBlank();
		if (!isInternal && (caller == null || caller != uid)) throw new ForbiddenException("Not allowed");
		return ResponseEntity.ok(svc.isMember(id, uid));
	}

	/** Platform-admin only (admin dashboard room list). */
	@GetMapping
	public ResponseEntity<?> all(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@RequestHeader("X-User-Id") int uid,
			@RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
		if (!RoomAccess.isPlatformAdmin(role)) throw new ForbiddenException("Admin access required");
		if (page != null && size != null) {
			return ResponseEntity.ok(svc.getAllRoomsPaged(page, size).map(r -> access.sanitize(r, uid)));
		}
		return ResponseEntity.ok(svc.getAllRooms().stream().map(r -> access.sanitize(r, uid)).toList());
	}

	/** Active room count for admin stats strip and analytics (rooms with at least one message). */
	@GetMapping("/count/active")
	public ResponseEntity<Long> countActive() {
		return ResponseEntity.ok(svc.countActiveRooms());
	}

	/** Internal (websocket-service) — bumps the room's last-message preview. */
	@PutMapping("/{id}/timestamp")
	public ResponseEntity<Void> updateTimestamp(@PathVariable String id,
			@RequestBody(required = false) java.util.Map<String, Object> body,
			@RequestHeader(value = "X-Internal-Service", required = false) String internal) {
		if (internal == null || internal.isBlank()) throw new ForbiddenException("Internal endpoint");
		String preview = body != null ? (String) body.get("preview") : null;
		Integer senderId = body != null && body.get("senderId") != null
				? Integer.parseInt(body.get("senderId").toString()) : null;
		svc.updateLastMessageAt(id, preview, senderId);
		return ResponseEntity.noContent().build();
	}

	// ─── P2-14: Room Search ──────────────────────────────────────────

	@GetMapping("/search")
	@Operation(summary = "Search public rooms by keyword")
	public ResponseEntity<List<Room>> search(@RequestParam String q, @RequestHeader("X-User-Id") int uid) {
		return ResponseEntity.ok(svc.searchRooms(q).stream().map(r -> access.sanitize(r, uid)).toList());
	}

	// ─── P2-13: Room Invite Links ────────────────────────────────────

	@PostMapping("/{id}/invite")
	@Operation(summary = "Generate invite code for a room (creator only)")
	public ResponseEntity<Map<String, String>> generateInviteCode(
			@PathVariable String id,
			@RequestHeader("X-User-Id") int uid) {
		String code = svc.generateInviteCode(id, uid);
		return ResponseEntity.ok(Map.of("inviteCode", code));
	}

	@GetMapping("/join/{code}")
	@Operation(summary = "Preview a room from its invite code", description = "Minimal public info shown on the join page; no membership needed")
	public ResponseEntity<com.connecthub.room.dto.RoomPreviewDto> previewByInvite(
			@PathVariable String code,
			@RequestHeader("X-User-Id") int uid) {
		inviteLimiter.check(uid);
		return ResponseEntity.ok(svc.previewByInviteCode(code));
	}

	@PostMapping("/join/{code}")
	@Operation(summary = "Join a room by invite code")
	public ResponseEntity<RoomMember> joinByInvite(
			@PathVariable String code,
			@RequestHeader("X-User-Id") int uid) {
		inviteLimiter.check(uid);
		return ResponseEntity.ok(svc.joinByInviteCode(code, uid));
	}

	@DeleteMapping("/{id}/invite")
	@Operation(summary = "Revoke invite code for a room (creator only)")
	public ResponseEntity<Void> revokeInviteCode(
			@PathVariable String id,
			@RequestHeader("X-User-Id") int uid) {
		svc.revokeInviteCode(id, uid);
		return ResponseEntity.noContent().build();
	}
}
