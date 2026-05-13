package com.connecthub.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "analytics_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalyticsSnapshot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    @CreationTimestamp @Column(updatable = false)
    private LocalDateTime snapshotAt;

    @Column(nullable = false) private int    onlineCount;
    @Column(nullable = false) private long   totalUsers;
    @Column(nullable = false) private long   activeUsers;
    @Column(nullable = false) private long   suspendedUsers;
    @Column(nullable = false) private long   messagesToday;
    @Column(nullable = false) private long   activeRooms;
    @Column(nullable = false) private double storageMb;
}
