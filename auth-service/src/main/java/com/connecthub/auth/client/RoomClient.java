package com.connecthub.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "room-service")
public interface RoomClient {

    /** Returns the number of rooms with message activity in the last 24 hours. */
    @GetMapping("/api/v1/rooms/count/active")
    long countActiveRooms();
}
