package com.connecthub.room.service;

import com.connecthub.room.client.AuthClient;
import com.connecthub.room.exception.BadRequestException;
import com.connecthub.room.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks that user ids really belong to accounts, asking auth-service (the owner of accounts). Without this a room
 * could be created with members that never existed or were deleted, which then show up as phantom participants.
 * Fails closed: if auth-service cannot answer, the request is refused (503) rather than accepted unchecked.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDirectory {

    private final AuthClient authClient;

    /** @throws BadRequestException naming every id that does not exist */
    public void requireExist(Collection<Integer> userIds, int callerId) {
        if (userIds == null || userIds.isEmpty()) return;
        Set<Integer> wanted = new LinkedHashSet<>(userIds);
        Set<Integer> existing = new HashSet<>();
        try {
            List<Map<String, Object>> found = authClient.getUsersByIds(List.copyOf(wanted), String.valueOf(callerId));
            if (found != null) {
                for (Map<String, Object> u : found) {
                    Object id = u.get("userId");
                    if (id != null) existing.add(Integer.valueOf(String.valueOf(id)));
                }
            }
        } catch (Exception e) {
            log.warn("User lookup failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Could not verify the members right now. Please try again.");
        }
        Set<Integer> missing = new TreeSet<>(wanted);
        missing.removeAll(existing);
        if (!missing.isEmpty()) {
            throw new BadRequestException("Unknown user id(s): " + missing);
        }
    }
}
