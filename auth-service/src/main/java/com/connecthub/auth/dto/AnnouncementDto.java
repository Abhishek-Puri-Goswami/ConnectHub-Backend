package com.connecthub.auth.dto;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AnnouncementDto {
    private Long id;
    private String title;
    private String content;
    private Integer adminId;
    private String adminName;
    private LocalDateTime createdAt;
}
