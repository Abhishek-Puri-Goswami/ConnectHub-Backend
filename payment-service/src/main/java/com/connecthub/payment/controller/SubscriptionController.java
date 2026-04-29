package com.connecthub.payment.controller;

import com.connecthub.payment.dto.CreateSubscriptionRequest;
import com.connecthub.payment.dto.PaymentResponse;
import com.connecthub.payment.dto.SubscriptionResponse;
import com.connecthub.payment.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Subscription management endpoints.
 * All routes require authentication (JWT via X-User-Id header injected by
 * gateway).
 */
@RestController
@RequestMapping("/api/v1/payments/subscription")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Subscription", description = "Manage ConnectHub PRO subscriptions via Razorpay")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Creates (or returns existing) one-time Razorpay order for the calling user.
     * The returned razorpayOrderId is passed to Razorpay Checkout on the frontend.
     */
    @PostMapping("/create")
    @Operation(summary = "Create payment order", description = "Initiates a one-time Razorpay order for the authenticated user")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @Valid @RequestBody CreateSubscriptionRequest req) {

        req.setUserId(userId);
        SubscriptionResponse response = subscriptionService.createOrder(userId, userEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns frontend checkout config (Razorpay key + amount in paise).
     */
    @GetMapping("/config")
    @Operation(summary = "Get checkout config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(subscriptionService.getConfig());
    }

    /**
     * Returns the current subscription status for the calling user.
     */
    @GetMapping("/status")
    @Operation(summary = "Get subscription status")
    public ResponseEntity<SubscriptionResponse> getStatus(
            @RequestHeader("X-User-Id") Integer userId) {
        return subscriptionService.getSubscription(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns payment history for the calling user's subscription.
     */
    @GetMapping("/payments")
    @Operation(summary = "Get payment history")
    public ResponseEntity<List<PaymentResponse>> getPaymentHistory(
            @RequestHeader("X-User-Id") Integer userId) {
        List<PaymentResponse> history = subscriptionService.getPaymentHistory(userId);
        return ResponseEntity.ok(history);
    }
}
