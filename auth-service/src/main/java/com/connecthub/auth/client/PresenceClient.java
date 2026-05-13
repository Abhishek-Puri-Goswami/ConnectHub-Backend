package com.connecthub.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "presence-service")
public interface PresenceClient {

    @GetMapping("/api/v1/presence/online/count")
    int getOnlineCount();
}
