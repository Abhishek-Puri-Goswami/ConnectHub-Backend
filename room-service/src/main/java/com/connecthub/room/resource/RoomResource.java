package com.connecthub.room.resource;

import com.connecthub.room.dto.CreateRoomRequest;
import com.connecthub.room.entity.*;
import com.connecthub.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room and channel management")
public class RoomResource {
	private final RoomService svc;

	@PostMapping
	@Operation(summary = "Create room (GROUP or DM)")
	public ResponseEntity<Room> create(
			@RequestHeader("X-User-Id") int uid,
			@RequestHeader(value = "X-Subscription-Tier", required = false) String subscriptionTier,
			@Valid @RequestBody CreateRoomRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(svc.createRoom(uid, req, subscriptionTier));
	}

	@GetMapping("/{id}")
	public ResponseEntity<Room> get(@PathVariable String id) {
		return svc.getRoom(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/user/{uid}")
	public ResponseEntity<List<Room>> byUser(@PathVariable int uid) {
		return ResponseEntity.ok(svc.getRoomsByUser(uid));
	}

	@PutMapping("/{id}")
	public ResponseEntity<Room> update(@PathVariable String id, @RequestBody Room r) {
		return ResponseEntity.ok(svc.updateRoom(id, r));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		svc.deleteRoom(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/members/{uid}")
	public ResponseEntity<RoomMember> addMember(@PathVariable String id, @PathVariable int uid,
			@RequestParam(defaultValue = "MEMBER") String role) {
		return ResponseEntity.status(HttpStatus.CREATED).body(svc.addMember(id, uid, role));
	}

	@DeleteMapping("/{id}/members/{uid}")
	public ResponseEntity<Void> removeMember(@PathVariable String id, @PathVariable int uid) {
		svc.removeMember(id, uid);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/members")
	public ResponseEntity<List<RoomMember>> members(@PathVariable String id) {
		return ResponseEntity.ok(svc.getMembers(id));
	}

	@PutMapping("/{id}/members/{uid}/role")
	public ResponseEntity<Void> role(@PathVariable String id, @PathVariable int uid,
			@RequestBody Map<String, String> b) {
		svc.updateRole(id, uid, b.get("role"));
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/members/{uid}/mute")
	public ResponseEntity<Void> mute(@PathVariable String id, @PathVariable int uid, @RequestParam boolean muted) {
		svc.mute(id, uid, muted);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/read/{uid}")
	public ResponseEntity<Void> read(@PathVariable String id, @PathVariable int uid) {
		svc.updateLastRead(id, uid);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/pin/{msgId}")
	public ResponseEntity<Void> pin(@PathVariable String id, @PathVariable String msgId) {
		svc.pinMessage(id, msgId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}/pin")
	public ResponseEntity<Void> unpin(@PathVariable String id) {
		svc.pinMessage(id, null);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/members/{uid}/check")
	public ResponseEntity<Boolean> check(@PathVariable String id, @PathVariable int uid) {
		return ResponseEntity.ok(svc.isMember(id, uid));
	}

	@GetMapping
	public ResponseEntity<?> all(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		if (page != null && size != null) {
			return ResponseEntity.ok(svc.getAllRoomsPaged(page, size));
		}
		return ResponseEntity.ok(svc.getAllRooms());
	}

	/** Active room count for admin stats strip and analytics (rooms with at least one message). */
	@GetMapping("/count/active")
	public ResponseEntity<Long> countActive() {
		return ResponseEntity.ok(svc.countActiveRooms());
	}

	@PutMapping("/{id}/timestamp")
	public ResponseEntity<Void> updateTimestamp(@PathVariable String id,
			@RequestBody(required = false) java.util.Map<String, Object> body) {
		String preview = body != null ? (String) body.get("preview") : null;
		Integer senderId = body != null && body.get("senderId") != null
				? Integer.parseInt(body.get("senderId").toString()) : null;
		svc.updateLastMessageAt(id, preview, senderId);
		return ResponseEntity.noContent().build();
	}

	// ─── P2-14: Room Search ──────────────────────────────────────────

	@GetMapping("/search")
	@Operation(summary = "Search public rooms by keyword")
	public ResponseEntity<List<Room>> search(@RequestParam String q) {
		return ResponseEntity.ok(svc.searchRooms(q));
	}

	// ─── P2-13: Room Invite Links ────────────────────────────────────

	@PostMapping("/{id}/invite")
	@Operation(summary = "Generate invite code for a room (admin only)")
	public ResponseEntity<Map<String, String>> generateInviteCode(
			@PathVariable String id,
			@RequestHeader("X-User-Id") int uid) {
		String code = svc.generateInviteCode(id, uid);
		return ResponseEntity.ok(Map.of("inviteCode", code));
	}

	@PostMapping("/join/{code}")
	@Operation(summary = "Join a room by invite code")
	public ResponseEntity<RoomMember> joinByInvite(
			@PathVariable String code,
			@RequestHeader("X-User-Id") int uid) {
		return ResponseEntity.ok(svc.joinByInviteCode(code, uid));
	}

	@DeleteMapping("/{id}/invite")
	@Operation(summary = "Revoke invite code for a room (admin only)")
	public ResponseEntity<Void> revokeInviteCode(
			@PathVariable String id,
			@RequestHeader("X-User-Id") int uid) {
		svc.revokeInviteCode(id, uid);
		return ResponseEntity.noContent().build();
	}
}

