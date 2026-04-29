package com.connecthub.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PinMessagePayload {
    private String roomId;
    /** null when unpinning */
    private String messageId;
    private Integer pinnedBy;
    private long timestamp;
}
