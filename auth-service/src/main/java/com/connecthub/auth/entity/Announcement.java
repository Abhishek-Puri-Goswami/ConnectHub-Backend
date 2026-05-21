package com.connecthub.auth.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "announcements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 200) private String title;
    @Column(length = 2000, nullable = false) private String content;
    private Integer adminId;
    @Column(length = 100) private String adminName;
    @CreationTimestamp @Column(updatable = false) private LocalDateTime createdAt;
}
