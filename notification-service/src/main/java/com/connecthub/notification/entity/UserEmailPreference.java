package com.connecthub.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_email_preferences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserEmailPreference {

    @Id
    private Integer userId;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private boolean emailNotificationsEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
