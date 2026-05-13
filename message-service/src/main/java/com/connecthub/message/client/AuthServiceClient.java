package com.connecthub.message.client;

import com.connecthub.message.dto.UserSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * AuthServiceClient — Feign client for resolving @mention usernames to user IDs.
 *
 * Called by MessageService.send() when a message contains @username patterns.
 * Returns 404 if the username doesn't exist, which the caller catches and ignores.
 */
@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/api/v1/auth/users/by-username/{username}")
    UserSummaryDto getUserByUsername(@PathVariable("username") String username);
}
