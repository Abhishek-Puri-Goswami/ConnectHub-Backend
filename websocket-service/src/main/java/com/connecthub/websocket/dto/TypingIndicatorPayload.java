package com.connecthub.websocket.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class TypingIndicatorPayload {
    private Integer senderId;
    private String senderUsername;
    private String roomId;
    private boolean typing;
}
