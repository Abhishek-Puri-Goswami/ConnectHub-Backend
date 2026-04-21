package com.connecthub.websocket.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactionPayload {
    private Integer senderId;
    private String messageId;
    private String roomId;
    private String emoji;
    private String action;
}
