package com.connecthub.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tokenId;

    @Column(nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 255)
    private String fcmToken;

    @Column(nullable = false, length = 20)
    private String platform;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
