package com.connecthub.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Lightweight reply preview attached to messages that reference another message. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagePreviewDto {
    private String messageId;
    private Integer senderId;
    private String contentPreview;
    private LocalDateTime sentAt;
}
