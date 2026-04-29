package com.connecthub.room.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;

@Entity @Table(name = "rooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {
    @Id @GeneratedValue(generator = "uuid2") @GenericGenerator(name = "uuid2", strategy = "uuid2") @Column(length = 36)
    private String roomId;
    @Column(length = 100) private String name;
    @Column(length = 500) private String description;
    @Column(nullable = false, length = 10) private String type;
    @Column(nullable = false) private Integer createdById;
    @Column(length = 500) private String avatarUrl;
    @Column(nullable = false) @Builder.Default private boolean isPrivate = false;
    @Builder.Default private int maxMembers = 500;
    private LocalDateTime lastMessageAt;
    @Column(length = 200) private String lastMessagePreview;
    private Integer lastMessageSenderId;
    @Column(length = 36) private String pinnedMessageId;
    /** P2-13: Shareable invite code for joining a room without a direct invitation */
    @Column(length = 20, unique = true) private String inviteCode;
    @CreationTimestamp @Column(updatable = false) private LocalDateTime createdAt;
}
