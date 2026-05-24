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
 * Handles all subscription and payment endpoints for ConnectHub plans.
 *
 * All routes sit behind the API Gateway which validates the JWT and injects
 * the caller's identity as X-User-Id and X-User-Email headers. The controller
 * reads those headers instead of parsing the token itself.
 *
 * Base path: /api/v1/payments/subscription
 */
// Handles HTTP requests and writes the return value directly as JSON (no view layer)
@RestController
// All methods in this class share this URL prefix
@RequestMapping("/api/v1/payments/subscription")
// Lombok: generates a constructor that injects all final fields
@RequiredArgsConstructor
// Lombok: injects a `log` field for SLF4J logging
@Slf4j
// Swagger: groups these endpoints under the "Subscription" section in API docs
@Tag(name = "Subscription", description = "Manage ConnectHub PRO subscriptions via Razorpay")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Creates a Razorpay order for the calling user (or returns an existing active plan).
     * The returned razorpayOrderId is passed directly to Razorpay Checkout on the frontend.
     * Accepts a plan field: "PREMIUM" (₹100/mo) or "PLATINUM" (₹149/mo).
     */
    // Handles POST /api/v1/payments/subscription/create
    @PostMapping("/create")
    // Swagger: describes this endpoint in the generated API documentation
    @Operation(summary = "Create payment order", description = "Initiates a one-time Razorpay order for the authenticated user")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @RequestHeader("X-User-Id") Integer userId,           // injected by API Gateway from JWT
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @Valid @RequestBody CreateSubscriptionRequest req) {   // @Valid triggers bean validation on the request body

        req.setUserId(userId);
        String plan = req.getPlan() != null ? req.getPlan().toUpperCase() : "PREMIUM";
        SubscriptionResponse response = subscriptionService.createOrder(userId, userEmail, plan);
        return ResponseEntity.ok(response);
    }

    /**
     * Verifies the Razorpay payment signature and activates the subscription immediately.
     * Called by the frontend right after a successful checkout — this is the primary
     * activation path and does not depend on Razorpay webhooks being delivered.
     */
    // Handles POST /api/v1/payments/subscription/verify
    @PostMapping("/verify")
    @Operation(summary = "Verify payment and activate subscription")
    public ResponseEntity<SubscriptionResponse> verifyPayment(
            @RequestHeader("X-User-Id") Integer userId,            // injected by API Gateway from JWT
            @RequestBody java.util.Map<String, String> body) {
        String paymentId = body.get("razorpay_payment_id");
        String orderId   = body.get("razorpay_order_id");
        String signature = body.get("razorpay_signature");
        if (paymentId == null || orderId == null || signature == null) {
            return ResponseEntity.badRequest().build();
        }
        SubscriptionResponse response = subscriptionService.verifyAndActivate(paymentId, orderId, signature);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels the subscription — the user keeps access until the current period ends.
     */
    // Handles POST /api/v1/payments/subscription/cancel
    @PostMapping("/cancel")
    @Operation(summary = "Cancel subscription")
    public ResponseEntity<Void> cancelSubscription(
            @RequestHeader("X-User-Id") Integer userId) {
        subscriptionService.cancelSubscription(userId);
        return ResponseEntity.ok().build();
    }

    /** Returns the Razorpay public key and plan amounts needed to open the checkout widget. */
    // Handles GET /api/v1/payments/subscription/config
    @GetMapping("/config")
    @Operation(summary = "Get checkout config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(subscriptionService.getConfig());
    }

    /** Returns the current plan and subscription status for the calling user. */
    // Handles GET /api/v1/payments/subscription/status
    @GetMapping("/status")
    @Operation(summary = "Get subscription status")
    public ResponseEntity<SubscriptionResponse> getStatus(
            @RequestHeader("X-User-Id") Integer userId) {
        return subscriptionService.getSubscription(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Returns all recorded payment transactions for the calling user. */
    // Handles GET /api/v1/payments/subscription/payments
    @GetMapping("/payments")
    @Operation(summary = "Get payment history")
    public ResponseEntity<List<PaymentResponse>> getPaymentHistory(
            @RequestHeader("X-User-Id") Integer userId) {
        List<PaymentResponse> history = subscriptionService.getPaymentHistory(userId);
        return ResponseEntity.ok(history);
    }
}
