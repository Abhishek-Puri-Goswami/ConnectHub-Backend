package com.connecthub.websocket.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class MessageEditPayload {
    private Integer editorId;
    private String messageId;
    private String roomId;
    private String newContent;
}
