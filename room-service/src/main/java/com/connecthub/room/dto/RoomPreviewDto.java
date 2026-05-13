package com.connecthub.room.dto;

import lombok.*;

/**
 * RoomPreviewDto — Safe public-facing snapshot of a room for the invite link landing page.
 *
 * Returned by GET /rooms/join/{code} to let a prospective member see room details
 * before committing to joining. Deliberately excludes sensitive fields:
 *   - inviteCode  — never exposed outside the room to non-members
 *   - createdById — internal field, not needed on the preview card
 *   - maxMembers  — internal capacity detail
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomPreviewDto {
    private String  roomId;
    private String  name;
    private String  description;
    private String  avatarUrl;
    private boolean isPrivate;
    private String  type;
    private long    memberCount;
}
