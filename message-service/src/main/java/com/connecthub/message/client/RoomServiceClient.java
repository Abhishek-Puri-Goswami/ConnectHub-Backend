package com.connecthub.message.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * RoomServiceClient — Feign client for room-service membership checks.
 *
 * PURPOSE:
 *   Used by MessageResource to verify that the requesting user is a member
 *   of the room before serving message history, search results, or unread counts.
 *   This enforces defense-in-depth: even if a client bypasses frontend routing,
 *   they cannot read messages from a room they haven't joined.
 *
 * FAILURE BEHAVIOUR (fail-closed):
 *   If room-service is unavailable, the Feign call throws an exception which
 *   MessageResource catches and converts to 403 Forbidden. We fail-closed
 *   (deny on error) rather than fail-open (allow on error) because data
 *   confidentiality is more important than availability for this check.
 *
 * LOAD BALANCING:
 *   The name "room-service" resolves via Eureka + Spring Cloud LoadBalancer
 *   to the actual running instance. No hardcoded URL needed.
 */
@FeignClient(name = "room-service")
public interface RoomServiceClient {

    /**
     * Returns true if {@code userId} is a member of the room with ID {@code roomId}.
     * Maps to GET /api/v1/rooms/{id}/members/{uid}/check in room-service.
     */
    @GetMapping("/api/v1/rooms/{roomId}/members/{userId}/check")
    boolean isMember(@PathVariable("roomId") String roomId,
                     @PathVariable("userId") int userId);

    /**
     * Returns the role ("ADMIN" or "MEMBER") of a user in a room.
     * Throws FeignException.NotFound (404) if the user is not a member.
     * Maps to GET /api/v1/rooms/{id}/members/{uid}/role in room-service.
     */
    @GetMapping("/api/v1/rooms/{roomId}/members/{userId}/role")
    String getMemberRole(@PathVariable("roomId") String roomId,
                         @PathVariable("userId") int userId);
}
