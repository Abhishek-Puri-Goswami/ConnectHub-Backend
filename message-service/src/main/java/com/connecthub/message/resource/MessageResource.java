package com.connecthub.message.resource;

import com.connecthub.message.config.AppConstants;
import com.connecthub.message.config.SubscriptionTierLimits;
import com.connecthub.message.entity.*;
import com.connecthub.message.exception.ForbiddenException;
import com.connecthub.message.service.MessageService;
import com.connecthub.message.service.RoomAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Chat message lifecycle")
@SuppressWarnings("java:S4684")
public class MessageResource {
	private static final int MAX_PAGE_SIZE = 100;
	private final MessageService svc;
	private final RoomAccess access;

	@PostMapping
	public ResponseEntity<Message> send(@RequestBody Message msg,
			@RequestHeader(AppConstants.HEADER_USER_ID) int uid,
			@RequestHeader(value = AppConstants.HEADER_SUBSCRIPTION_TIER, required = false) String subscriptionTier) {
		access.requireMember(msg.getRoomId(), uid);
		msg.setSenderId(uid); // never trust a sender id from the body
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(svc.send(msg, SubscriptionTierLimits.normalizeTier(subscriptionTier)));
	}

	@GetMapping("/room/{roomId}")
	@Operation(summary = "Get messages (cursor-based)", description = "Pass ?before={sentAt} for pagination, ?limit= for page size")
	public ResponseEntity<List<Message>> get(@PathVariable String roomId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
			@RequestParam(defaultValue = "50") int limit,
			@RequestHeader(AppConstants.HEADER_USER_ID) int uid) {
		access.requireMember(roomId, uid);
		return ResponseEntity.ok(svc.getMessages(roomId, before, Math.min(Math.max(limit, 1), MAX_PAGE_SIZE)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<Message> edit(@PathVariable String id, @RequestHeader("X-User-Id") int uid,
			@RequestBody Map<String, String> b) {
		return ResponseEntity.ok(svc.edit(id, b.get("content"), uid));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id, @RequestHeader("X-User-Id") int uid) {
		svc.delete(id, uid);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/room/{roomId}/search")
	public ResponseEntity<List<Message>> search(@PathVariable String roomId, @RequestParam String keyword,
			@RequestHeader(AppConstants.HEADER_USER_ID) int uid) {
		access.requireMember(roomId, uid);
		return ResponseEntity.ok(svc.search(roomId, keyword));
	}

	/** Internal only — delivery-status updates come from other services, never from end users. */
	@PutMapping("/{id}/status")
	public ResponseEntity<Void> status(@PathVariable String id, @RequestBody Map<String, String> b,
			@RequestHeader(value = "X-Internal-Service", required = false) String internal) {
		requireInternal(internal);
		svc.updateStatus(id, b.get("status"));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/room/{roomId}/unread")
	public ResponseEntity<Long> unread(@PathVariable String roomId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastReadAt,
			@RequestHeader(AppConstants.HEADER_USER_ID) int uid) {
		access.requireMember(roomId, uid);
		return ResponseEntity.ok(svc.unreadCount(roomId, lastReadAt));
	}

	/** Wipes a room's history: room admins (or platform admins) only. */
	@DeleteMapping("/room/{roomId}/clear")
	public ResponseEntity<Void> clear(@PathVariable String roomId,
			@RequestHeader(AppConstants.HEADER_USER_ID) int uid,
			@RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
		if (!RoomAccess.isPlatformAdmin(role)) access.requireRoomAdmin(roomId, uid);
		svc.clearHistory(roomId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/reactions")
	public ResponseEntity<MessageReaction> react(@PathVariable String id, @RequestHeader("X-User-Id") int uid,
			@RequestBody Map<String, String> b) {
		access.requireMember(svc.roomIdOf(id), uid);
		return ResponseEntity.status(HttpStatus.CREATED).body(svc.addReaction(id, uid, b.get("emoji")));
	}

	@DeleteMapping("/{id}/reactions")
	public ResponseEntity<Void> unreact(@PathVariable String id, @RequestHeader("X-User-Id") int uid,
			@RequestParam String emoji) {
		access.requireMember(svc.roomIdOf(id), uid);
		svc.removeReaction(id, uid, emoji);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/reactions")
	public ResponseEntity<List<MessageReaction>> reactions(@PathVariable String id,
			@RequestHeader(AppConstants.HEADER_USER_ID) int uid) {
		access.requireMember(svc.roomIdOf(id), uid);
		return ResponseEntity.ok(svc.getReactions(id));
	}

	/** Internal only — used by auth-service analytics. */
	@GetMapping("/count/today")
	public ResponseEntity<Long> countToday(
			@RequestHeader(value = "X-Internal-Service", required = false) String internal,
			@RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
		if ((internal == null || internal.isBlank()) && !RoomAccess.isPlatformAdmin(role))
			throw new ForbiddenException("Not allowed");
		return ResponseEntity.ok(svc.countToday());
	}

	/** Internal only — websocket-service uses this to verify authorship before relaying edit/delete events. */
	@GetMapping("/{id}/sender")
	public ResponseEntity<Map<String, Object>> sender(@PathVariable String id,
			@RequestHeader(value = "X-Internal-Service", required = false) String internal) {
		requireInternal(internal);
		return ResponseEntity.ok(svc.senderInfo(id));
	}

	private static void requireInternal(String internal) {
		if (internal == null || internal.isBlank()) throw new ForbiddenException("Internal endpoint");
	}
}
