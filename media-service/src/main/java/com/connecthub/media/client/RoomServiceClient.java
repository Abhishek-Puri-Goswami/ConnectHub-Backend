package com.connecthub.media.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * RoomServiceClient — Feign client for checking room membership via room-service.
 *
 * PURPOSE:
 *   Used by media-service to verify that a requesting user is a member of the room
 *   associated with a media file before granting access. This prevents unauthorized
 *   users from accessing media files shared in private rooms.
 */
@FeignClient(name = "room-service")
public interface RoomServiceClient {

    /**
     * isMember — returns true if the user is a member of the given room.
     * Delegates to room-service's GET /api/v1/rooms/{roomId}/members/{userId}/check endpoint.
     */
    @GetMapping("/api/v1/rooms/{roomId}/members/{userId}/check")
    Boolean isMember(@PathVariable("roomId") String roomId, @PathVariable("userId") int userId);
}
