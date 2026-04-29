package com.connecthub.websocket.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    /** Returns a list of user profile maps matching the given query (username/email prefix). */
    @GetMapping("/api/v1/auth/search")
    List<Map<String, Object>> searchUsers(@RequestParam("q") String query);
}
