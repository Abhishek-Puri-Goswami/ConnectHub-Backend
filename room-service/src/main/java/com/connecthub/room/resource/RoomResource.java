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
	public ResponseEntity<List<Room>> all() {
		return ResponseEntity.ok(svc.getAllRooms());
	}

	@PutMapping("/{id}/timestamp")
	public ResponseEntity<Void> updateTimestamp(@PathVariable String id) {
		svc.updateLastMessageAt(id);
		return ResponseEntity.noContent().build();
	}
}
