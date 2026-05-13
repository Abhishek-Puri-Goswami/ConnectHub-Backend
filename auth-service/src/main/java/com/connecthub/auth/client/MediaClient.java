package com.connecthub.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "media-service")
public interface MediaClient {

    /** Returns total storage used by all platform files in megabytes. */
    @GetMapping("/api/v1/media/storage/total")
    double getTotalStorageMb();
}
