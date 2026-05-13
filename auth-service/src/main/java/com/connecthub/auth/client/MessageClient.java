package com.connecthub.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "message-service")
public interface MessageClient {

    /** Returns the count of non-deleted messages sent since midnight today. */
    @GetMapping("/api/v1/messages/count/today")
    long countToday();
}
