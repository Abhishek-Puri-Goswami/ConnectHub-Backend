package com.connecthub.payment.service;

import com.connecthub.payment.dto.*;
import com.connecthub.payment.entity.Payment;
import com.connecthub.payment.entity.Subscription;
import com.connecthub.payment.repository.PaymentRepository;
import com.connecthub.payment.repository.SubscriptionRepository;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SubscriptionService {

    private final RazorpayClient razorpayClient;
    private final SubscriptionRepository subscriptionRepo;
    private final PaymentRepository paymentRepo;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redis;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${payment.premium.amount-paise:10000}")
    private long premiumAmountPaise;

    @Value("${payment.platinum.amount-paise:14900}")
    private long platinumAmountPaise;

    /** Duration of a paid subscription in months. */
    private static final int SUBSCRIPTION_MONTHS = 1;

    /**
     * createOrder — creates a Razorpay Order for a PREMIUM or PLATINUM upgrade payment.
     *
     * Flow:
     *   1. Normalize the plan to PREMIUM or PLATINUM (defaults to PREMIUM for unknown values).
     *   2. If the user already has an ACTIVE paid plan that hasn't expired, return existing.
     *   3. Create a Razorpay Order with the tier-specific amount (paise).
     *   4. Persist a local Subscription row with status=PENDING, the plan, and the order ID.
     *   5. Return the order ID to the frontend to open the Razorpay widget.
     */
    public SubscriptionResponse createOrder(Integer userId, String userEmail, String requestedPlan) {
        // Normalize to valid tier — unknown values default to PREMIUM
        String plan = "PLATINUM".equalsIgnoreCase(requestedPlan) ? "PLATINUM" : "PREMIUM";
        long amountPaise = "PLATINUM".equals(plan) ? platinumAmountPaise : premiumAmountPaise;

        Optional<Subscription> existing = subscriptionRepo.findByUserId(userId);
        if (existing.isPresent()) {
            Subscription sub = existing.get();
            boolean activeAndNotExpired = "ACTIVE".equalsIgnoreCase(sub.getStatus())
                    && !"FREE".equalsIgnoreCase(sub.getPlan())
                    && (sub.getEndDate() == null || sub.getEndDate().isAfter(LocalDateTime.now()));
            if (activeAndNotExpired) {
                log.info("User {} already has an active {} plan", userId, sub.getPlan());
                return toResponse(sub);
            }
        }

        try {
            JSONObject options = new JSONObject();
            options.put("amount", amountPaise);
            options.put("currency", "INR");
            options.put("receipt", "connecthub_" + plan.toLowerCase() + "_" + userId);
            JSONObject notes = new JSONObject();
            notes.put("userId", userId);
            notes.put("plan", plan);
            options.put("notes", notes);

            com.razorpay.Order rzpOrder = razorpayClient.orders.create(options);
            String rzpOrderId = rzpOrder.get("id");

            Subscription sub = existing.orElseGet(Subscription::new);
            sub.setUserId(userId);
            sub.setUserEmail(userEmail);
            sub.setPlan(plan);
            sub.setStatus("PENDING");
            sub.setRazorpayOrderId(rzpOrderId);
            sub.setStartDate(LocalDateTime.now());
            sub.setEndDate(null);

            sub = subscriptionRepo.save(sub);
            log.info("Created Razorpay order {} ({}) for user {}", rzpOrderId, plan, userId);
            return toResponse(sub);

        } catch (Exception e) {
            log.error("Failed to create Razorpay order for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Could not create payment order: " + e.getMessage(), e);
        }
    }

    /**
     * verifyAndActivate — verifies a completed Razorpay payment using the frontend-supplied
     * signature and activates the plan immediately.
     *
     * The Razorpay checkout handler fires on the client with:
     *   { razorpay_payment_id, razorpay_order_id, razorpay_signature }
     * The signature = HMAC-SHA256(orderId + "|" + paymentId, keySecret).
     * We verify this here and activate the subscription, making the flow independent of
     * webhook delivery.
     */
    public SubscriptionResponse verifyAndActivate(String razorpayPaymentId,
                                                   String razorpayOrderId,
                                                   String signature) {
        // Verify Razorpay payment signature
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", razorpayOrderId);
            attributes.put("razorpay_payment_id", razorpayPaymentId);
            attributes.put("razorpay_signature", signature);
            boolean valid = com.razorpay.Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
            if (!valid) throw new RuntimeException("Invalid payment signature");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Payment signature verification failed: {}", e.getMessage());
            throw new RuntimeException("Payment verification failed: " + e.getMessage());
        }

        Subscription sub = subscriptionRepo.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + razorpayOrderId));

        // Idempotent — skip if already activated
        if ("ACTIVE".equalsIgnoreCase(sub.getStatus())) {
            log.debug("Order {} already active — skipping re-activation", razorpayOrderId);
            return toResponse(sub);
        }

        // Record payment if not already saved by webhook
        if (paymentRepo.findByRazorpayPaymentId(razorpayPaymentId).isEmpty()) {
            paymentRepo.save(Payment.builder()
                    .subscriptionId(sub.getId())
                    .razorpayPaymentId(razorpayPaymentId)
                    .razorpayOrderId(razorpayOrderId)
                    .amount(BigDecimal.ZERO)
                    .currency("INR")
                    .status("CAPTURED")
                    .build());
        }

        activatePlan(sub);
        log.info("Plan activated via frontend verify for order {}", razorpayOrderId);
        return toResponse(sub);
    }

    /**
     * handleWebhookEvent — routes Razorpay webhook events to the appropriate handler.
     */
    public void handleWebhookEvent(String event, JSONObject payload) {
        log.info("Processing Razorpay webhook event: {}", event);
        switch (event) {
            case "payment.captured" -> recordPayment(payload, "CAPTURED");
            case "payment.failed"   -> recordPayment(payload, "FAILED");
            default -> log.debug("Unhandled webhook event: {}", event);
        }
    }

    /**
     * recordPayment — persists a payment transaction from a Razorpay webhook.
     * For CAPTURED payments, also activates the Premium plan and publishes a Kafka event.
     */
    private void recordPayment(JSONObject payload, String status) {
        try {
            JSONObject entity = payload.getJSONObject("payment").getJSONObject("entity");
            String rzpPayId    = entity.getString("id");
            String rzpOrderId  = entity.optString("order_id", null);
            long   amountPaisa = entity.getLong("amount");
            String currency    = entity.optString("currency", "INR");

            if (paymentRepo.findByRazorpayPaymentId(rzpPayId).isPresent()) {
                log.debug("Payment {} already recorded — skipping", rzpPayId);
                return;
            }

            Optional<Subscription> subOpt = rzpOrderId != null
                    ? subscriptionRepo.findByRazorpayOrderId(rzpOrderId)
                    : Optional.empty();

            if (subOpt.isEmpty()) {
                JSONObject notes = entity.optJSONObject("notes");
                if (notes != null && notes.has("userId")) {
                    subOpt = subscriptionRepo.findByUserId(notes.getInt("userId"));
                    // If the webhook notes carry the plan, update the subscription record
                    if (subOpt.isPresent() && notes.has("plan")) {
                        String notedPlan = notes.getString("plan");
                        if ("PREMIUM".equals(notedPlan) || "PLATINUM".equals(notedPlan)) {
                            subOpt.get().setPlan(notedPlan);
                        }
                    }
                }
            }

            Long subId = subOpt.map(Subscription::getId).orElse(null);

            paymentRepo.save(Payment.builder()
                    .subscriptionId(subId)
                    .razorpayPaymentId(rzpPayId)
                    .razorpayOrderId(rzpOrderId)
                    .amount(BigDecimal.valueOf(amountPaisa, 2))
                    .currency(currency)
                    .status(status)
                    .build());

            log.info("Payment {} recorded with status {}", rzpPayId, status);

            if ("CAPTURED".equals(status)) {
                subOpt.ifPresent(this::activatePlan);
                sendReceiptEmail(subId, amountPaisa);
            }
        } catch (Exception e) {
            log.error("Failed to record payment: {}", e.getMessage());
        }
    }

    /**
     * activatePlan — marks the subscription ACTIVE with a 1-month endDate.
     * Preserves the plan tier (PREMIUM/PLATINUM) already stored on the subscription.
     * Publishes a Kafka event so auth-service can update the user's JWT tier claim.
     */
    private void activatePlan(Subscription sub) {
        // Normalize legacy "PRO" plan to "PREMIUM" for consistency
        String plan = "PRO".equalsIgnoreCase(sub.getPlan()) ? "PREMIUM" : sub.getPlan();
        LocalDateTime now = LocalDateTime.now();
        sub.setStatus("ACTIVE");
        sub.setPlan(plan);
        sub.setStartDate(now);
        sub.setEndDate(now.plusMonths(SUBSCRIPTION_MONTHS));
        subscriptionRepo.save(sub);
        publishSubscriptionEvent(sub.getUserId(), plan);
        log.info("{} plan activated for user {} — expires {}", plan, sub.getUserId(), sub.getEndDate());
    }

    /**
     * cancelSubscription — cancels a user's paid subscription at end of current period.
     * The user keeps access until endDate. Publishes a Kafka event for auth-service.
     */
    public void cancelSubscription(Integer userId) {
        Subscription sub = subscriptionRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No subscription found for user " + userId));
        if ("FREE".equalsIgnoreCase(sub.getPlan())) {
            throw new RuntimeException("Free plan cannot be cancelled");
        }
        sub.setStatus("CANCELLED");
        subscriptionRepo.save(sub);
        log.info("Subscription cancelled for user {} — access until {}", userId, sub.getEndDate());
    }

    /**
     * expireOverdueSubscriptions — called by the scheduler every hour.
     * Marks any ACTIVE subscription whose endDate has passed as EXPIRED,
     * then publishes a Kafka event to reset the user's JWT tier claim to FREE.
     */
    public void expireOverdueSubscriptions() {
        List<Subscription> overdue =
                subscriptionRepo.findAllByStatusAndEndDateBefore("ACTIVE", LocalDateTime.now());
        if (overdue.isEmpty()) return;

        for (Subscription sub : overdue) {
            sub.setStatus("EXPIRED");
            subscriptionRepo.save(sub);
            publishSubscriptionEvent(sub.getUserId(), "FREE");
            log.info("Subscription expired for user {} (endDate={})", sub.getUserId(), sub.getEndDate());
        }
        log.info("Expired {} overdue Premium subscriptions", overdue.size());
    }

    private void sendReceiptEmail(Long subscriptionId, long amountPaisa) {
        if (subscriptionId == null) return;
        subscriptionRepo.findById(subscriptionId).ifPresent(sub -> {
            String email = sub.getUserEmail();
            if (email == null || email.isBlank()) return;
            String rupees = String.format("%.2f", amountPaisa / 100.0);
            // Use JSONObject to safely build the payload — avoids JSON injection from user-controlled fields.
            JSONObject msgJson = new JSONObject();
            msgJson.put("to", email);
            msgJson.put("purpose", "subscription_confirmation");
            msgJson.put("plan", sub.getPlan());
            msgJson.put("amount", "₹" + rupees);
            String msgPayload = msgJson.toString();
            try {
                redis.convertAndSend("email:send", msgPayload);
                log.info("Receipt email queued for user {}", sub.getUserId());
            } catch (Exception e) {
                log.warn("Failed to queue receipt email for user {}: {}", sub.getUserId(), e.getMessage());
            }
        });
    }

    /** getSubscription — returns the user's current plan record, if any. */
    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> getSubscription(Integer userId) {
        return subscriptionRepo.findByUserId(userId).map(this::toResponse);
    }

    /** getPaymentHistory — returns all payment transactions for a user, newest first. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentHistory(Integer userId) {
        return subscriptionRepo.findByUserId(userId)
                .map(sub -> paymentRepo.findBySubscriptionIdOrderByCreatedAtDesc(sub.getId())
                        .stream().map(this::toPaymentResponse).toList())
                .orElse(List.of());
    }

    /** getConfig — returns the Razorpay key and tier-specific amounts for the frontend. */
    @Transactional(readOnly = true)
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("razorpayKeyId", razorpayKeyId);
        config.put("premiumAmountPaise", premiumAmountPaise);
        config.put("platinumAmountPaise", platinumAmountPaise);
        return config;
    }

    /**
     * publishSubscriptionEvent — sends a valid JSON Kafka message so auth-service
     * can update the user's subscriptionTier in the database and JWT claims.
     *
     * Previously this used HashMap.toString() which produces {key=value} — NOT valid
     * JSON — causing auth-service's ObjectMapper.readValue() to throw JsonParseException
     * and silently drop every subscription update.
     */
    private void publishSubscriptionEvent(Integer userId, String plan) {
        // Build valid JSON manually — avoids ObjectMapper dependency and is unambiguous
        String json = String.format("{\"userId\":%d,\"status\":\"%s\"}", userId, plan);
        kafkaTemplate.send("user.subscription.status", String.valueOf(userId), json);
        log.debug("Published subscription event userId={} plan={}", userId, plan);
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return SubscriptionResponse.builder()
                .id(s.getId()).userId(s.getUserId()).plan(s.getPlan())
                .status(s.getStatus()).razorpayOrderId(s.getRazorpayOrderId())
                .startDate(s.getStartDate()).endDate(s.getEndDate())
                .createdAt(s.getCreatedAt()).build();
    }

    private PaymentResponse toPaymentResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId()).subscriptionId(p.getSubscriptionId())
                .razorpayPaymentId(p.getRazorpayPaymentId())
                .razorpayOrderId(p.getRazorpayOrderId())
                .amount(p.getAmount()).currency(p.getCurrency())
                .status(p.getStatus()).createdAt(p.getCreatedAt()).build();
    }
}
