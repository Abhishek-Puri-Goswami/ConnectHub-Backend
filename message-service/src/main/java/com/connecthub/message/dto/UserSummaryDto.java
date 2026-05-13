package com.connecthub.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Minimal user info returned by auth-service for @mention resolution. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSummaryDto {
    private int userId;
    private String username;
}
