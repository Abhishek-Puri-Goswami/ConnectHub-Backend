package com.connecthub.room.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/** Feign client for auth-service, the owner of user accounts. */
@FeignClient(name = "auth-service")
public interface AuthClient {

    /** Profiles of the users that exist among the given ids (unknown ids are simply absent from the result). */
    @PostMapping("/api/v1/auth/users/batch")
    List<Map<String, Object>> getUsersByIds(@RequestBody List<Integer> ids,
                                            @RequestHeader("X-User-Id") String callerId);
}
