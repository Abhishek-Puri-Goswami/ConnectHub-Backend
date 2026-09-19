package com.connecthub.message.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/** Feign client for room-service — used to authorize access to a room's messages. */
@FeignClient(name = "room-service")
public interface RoomClient {

    @GetMapping("/api/v1/rooms/{roomId}/members/{userId}/check")
    Boolean isMember(@PathVariable("roomId") String roomId, @PathVariable("userId") int userId);

    /** Returns the room's members (with roles); room-service answers 403 unless X-User-Id is a member. */
    @GetMapping("/api/v1/rooms/{roomId}/members")
    List<Map<String, Object>> members(@PathVariable("roomId") String roomId,
                                      @RequestHeader("X-User-Id") String userId);
}
