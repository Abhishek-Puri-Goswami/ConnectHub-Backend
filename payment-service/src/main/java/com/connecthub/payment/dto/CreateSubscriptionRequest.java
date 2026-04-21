package com.connecthub.payment.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class CreateSubscriptionRequest {
    /** Razorpay Plan ID to subscribe to (PRO or BUSINESS). */
    @NotBlank(message = "planId is required")
    private String planId;

    /** userId from the JWT — service resolves from X-User-Id header; UI can omit. */
    private Integer userId;

    /** Number of billing cycles (default 12 = 1 year). */
    private int totalCount = 12;
}
