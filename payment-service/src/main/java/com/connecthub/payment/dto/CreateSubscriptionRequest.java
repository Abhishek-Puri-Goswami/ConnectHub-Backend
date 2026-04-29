package com.connecthub.payment.dto;

import lombok.Data;

@Data
public class CreateSubscriptionRequest {
    /** userId from the JWT — injected from X-User-Id header by the controller; UI can omit. */
    private Integer userId;
}
