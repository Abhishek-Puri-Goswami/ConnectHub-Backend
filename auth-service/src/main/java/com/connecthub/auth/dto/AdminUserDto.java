package com.connecthub.auth.dto;

import com.connecthub.auth.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What the admin API returns about a user. Deliberately an explicit allow-list: the {@link User} entity also
 * holds the password hash and OAuth provider id, which must never leave auth-service.
 */
@Data @AllArgsConstructor @NoArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserDto {
    private Integer userId;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private String bio;
    private String status;
    private String provider;
    private boolean active;
    private boolean emailVerified;
    private boolean phoneVerified;
    private String role;
    private String subscriptionTier;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;

    public static AdminUserDto from(User u) {
        return AdminUserDto.builder()
                .userId(u.getUserId()).username(u.getUsername()).email(u.getEmail()).fullName(u.getFullName())
                .phoneNumber(u.getPhoneNumber()).avatarUrl(u.getAvatarUrl()).bio(u.getBio()).status(u.getStatus())
                .provider(u.getProvider()).active(u.isActive()).emailVerified(u.isEmailVerified())
                .phoneVerified(u.isPhoneVerified()).role(u.getRole()).subscriptionTier(u.getSubscriptionTier())
                .lastSeenAt(u.getLastSeenAt()).createdAt(u.getCreatedAt())
                .build();
    }
}
