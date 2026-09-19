package com.connecthub.room.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What someone holding an invite link may see before joining. Deliberately minimal: no room id, creator,
 * members, message data or the invite code itself.
 */
@Data @AllArgsConstructor @NoArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomPreviewDto {
    private String name;
    private String description;
    private String avatarUrl;
    /** Serialized as "isPrivate" (what the join page reads). */
    @JsonProperty("isPrivate")
    private boolean isPrivate;
    private int memberCount;
    private int maxMembers;
}
