package com.connecthub.payment.service;

import com.connecthub.payment.dto.SubscriptionResponse;
import com.connecthub.payment.entity.Subscription;
import com.connecthub.payment.repository.PaymentRepository;
import com.connecthub.payment.repository.SubscriptionRepository;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private RazorpayClient razorpayClient;
    @Mock private SubscriptionRepository subscriptionRepo;
    @Mock private PaymentRepository paymentRepo;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private StringRedisTemplate redis;

    @InjectMocks
    private SubscriptionService svc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(svc, "razorpayKeyId",       "test_key");
        ReflectionTestUtils.setField(svc, "premiumAmountPaise",  10000L);
        ReflectionTestUtils.setField(svc, "platinumAmountPaise", 14900L);
    }

    @Test
    void createOrder_existingActive_returnsExisting() {
        Subscription existing = Subscription.builder()
                .userId(1).status("ACTIVE").plan("PREMIUM").razorpayOrderId("order_1").build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(existing));

        SubscriptionResponse res = svc.createOrder(1, "test@test.com", "PREMIUM");

        assertEquals("order_1", res.getRazorpayOrderId());
        verify(subscriptionRepo, never()).save(any());
    }

    @Test
    void handleWebhookEvent_paymentCaptured_activatesAndRecords() {
        JSONObject payload = new JSONObject(
                "{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":10000,\"currency\":\"INR\"}}}");
        Subscription existing = Subscription.builder()
                .id(10L).userId(1).plan("PREMIUM").status("PENDING").razorpayOrderId("order_1").build();

        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(existing));

        svc.handleWebhookEvent("payment.captured", payload);

        assertEquals("ACTIVE",   existing.getStatus());
        assertEquals("PREMIUM",  existing.getPlan());
        verify(paymentRepo).save(argThat(p ->
                "pay_1".equals(p.getRazorpayPaymentId()) &&
                "CAPTURED".equals(p.getStatus()) &&
                10L == p.getSubscriptionId()
        ));
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("1"), anyString());
    }

    @Test
    void handleWebhookEvent_paymentFailed_recordsOnly() {
        JSONObject payload = new JSONObject(
                "{\"payment\":{\"entity\":{\"id\":\"pay_2\",\"order_id\":\"order_1\",\"amount\":10000,\"currency\":\"INR\"}}}");

        when(paymentRepo.findByRazorpayPaymentId("pay_2")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByRazorpayOrderId("order_1")).thenReturn(Optional.empty());

        svc.handleWebhookEvent("payment.failed", payload);

        verify(paymentRepo).save(argThat(p -> "FAILED".equals(p.getStatus())));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void handleWebhookEvent_idempotent_skipsExistingPayment() {
        JSONObject payload = new JSONObject(
                "{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":10000,\"currency\":\"INR\"}}}");

        when(paymentRepo.findByRazorpayPaymentId("pay_1"))
                .thenReturn(Optional.of(com.connecthub.payment.entity.Payment.builder().build()));

        svc.handleWebhookEvent("payment.captured", payload);

        verify(paymentRepo, never()).save(any());
    }

    @Test
    void handleWebhookEvent_unknownEvent_isIgnored() {
        svc.handleWebhookEvent("subscription.activated", new JSONObject());
        verifyNoInteractions(subscriptionRepo, paymentRepo, kafkaTemplate);
    }

    // ── expireOverdueSubscriptions ────────────────────────────────────────────

    @Test
    void expireOverdueSubscriptions_noOverdue_doesNothing() {
        when(subscriptionRepo.findAllByStatusAndEndDateBefore(eq("ACTIVE"), any()))
                .thenReturn(List.of());
        svc.expireOverdueSubscriptions();
        verify(subscriptionRepo, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void expireOverdueSubscriptions_expiresAndPublishesKafka() {
        Subscription sub = Subscription.builder()
                .id(1L).userId(42).plan("PREMIUM").status("ACTIVE").build();
        when(subscriptionRepo.findAllByStatusAndEndDateBefore(eq("ACTIVE"), any()))
                .thenReturn(List.of(sub));

        svc.expireOverdueSubscriptions();

        assertEquals("EXPIRED", sub.getStatus());
        verify(subscriptionRepo).save(sub);
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("42"), anyString());
    }

    // ── getSubscription ───────────────────────────────────────────────────────

    @Test
    void getSubscription_present_returnsResponse() {
        Subscription sub = Subscription.builder()
                .id(1L).userId(1).plan("PREMIUM").status("ACTIVE").build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(sub));

        var result = svc.getSubscription(1);

        assertTrue(result.isPresent());
        assertEquals("PREMIUM", result.get().getPlan());
    }

    @Test
    void getSubscription_absent_returnsEmpty() {
        when(subscriptionRepo.findByUserId(99)).thenReturn(Optional.empty());
        assertTrue(svc.getSubscription(99).isEmpty());
    }

    // ── getPaymentHistory ─────────────────────────────────────────────────────

    @Test
    void getPaymentHistory_noSubscription_returnsEmptyList() {
        when(subscriptionRepo.findByUserId(99)).thenReturn(Optional.empty());
        assertTrue(svc.getPaymentHistory(99).isEmpty());
    }

    @Test
    void getPaymentHistory_withSubscription_returnsMappedPayments() {
        Subscription sub = Subscription.builder().id(5L).userId(1).build();
        com.connecthub.payment.entity.Payment pay = com.connecthub.payment.entity.Payment.builder()
                .id(10L).subscriptionId(5L).razorpayPaymentId("pay_1")
                .status("CAPTURED").currency("INR")
                .amount(java.math.BigDecimal.valueOf(100)).build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(sub));
        when(paymentRepo.findBySubscriptionIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(pay));

        var result = svc.getPaymentHistory(1);

        assertEquals(1, result.size());
        assertEquals("pay_1", result.get(0).getRazorpayPaymentId());
    }

    // ── getConfig ─────────────────────────────────────────────────────────────

    @Test
    void getConfig_returnsKeyAndAmounts() {
        var config = svc.getConfig();
        assertEquals("test_key", config.get("razorpayKeyId"));
        assertEquals(10000L, config.get("premiumAmountPaise"));
        assertEquals(14900L, config.get("platinumAmountPaise"));
    }

    // ── createOrder — expired plan branch ────────────────────────────────────

    @Test
    void createOrder_expiredSubscription_createsNewOrder() throws Exception {
        // A subscription that is ACTIVE but past its endDate → treated as expired
        Subscription expired = Subscription.builder()
                .id(1L).userId(1).plan("PREMIUM").status("ACTIVE")
                .endDate(java.time.LocalDateTime.now().minusDays(1)).build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(expired));

        com.razorpay.Order rzpOrder = mock(com.razorpay.Order.class);
        when(rzpOrder.get("id")).thenReturn("order_new");
        com.razorpay.OrderClient mockOrders = mock(com.razorpay.OrderClient.class);
        razorpayClient.orders = mockOrders;
        when(mockOrders.create(any(JSONObject.class))).thenReturn(rzpOrder);
        when(subscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        SubscriptionResponse res = svc.createOrder(1, "test@test.com", "PREMIUM");

        assertEquals("PENDING", res.getStatus());
        verify(subscriptionRepo).save(any());
    }

    // ── createOrder — brand-new user (no existing subscription) ──────────────

    @Test
    void createOrder_noExistingSubscription_createsNewOrder() throws Exception {
        when(subscriptionRepo.findByUserId(2)).thenReturn(Optional.empty());

        com.razorpay.Order rzpOrder = mock(com.razorpay.Order.class);
        when(rzpOrder.get("id")).thenReturn("order_brand_new");
        com.razorpay.OrderClient mockOrders = mock(com.razorpay.OrderClient.class);
        razorpayClient.orders = mockOrders;
        when(mockOrders.create(any(JSONObject.class))).thenReturn(rzpOrder);
        when(subscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        SubscriptionResponse res = svc.createOrder(2, "new@test.com", "PREMIUM");

        assertEquals("PENDING", res.getStatus());
        verify(subscriptionRepo).save(any());
    }

    // ── activatePlan — legacy PRO plan normalised to PREMIUM ─────────────────

    @Test
    void activatePlan_proPlan_normalizedToPremium() {
        // Arrange: captured webhook where subscription was stored with plan="PRO"
        JSONObject payload = new JSONObject(
                "{\"payment\":{\"entity\":{\"id\":\"pay_pro\",\"order_id\":\"order_pro\",\"amount\":10000,\"currency\":\"INR\"}}}");
        Subscription proPlan = Subscription.builder()
                .id(20L).userId(5).plan("PRO").status("PENDING").razorpayOrderId("order_pro").build();

        when(paymentRepo.findByRazorpayPaymentId("pay_pro")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByRazorpayOrderId("order_pro")).thenReturn(Optional.of(proPlan));

        svc.handleWebhookEvent("payment.captured", payload);

        // PRO must be normalised to PREMIUM during activation
        assertEquals("PREMIUM", proPlan.getPlan());
        assertEquals("ACTIVE",  proPlan.getStatus());
    }

    // ── sendReceiptEmail — receipt queued when subscription found ─────────────

    @Test
    void sendReceiptEmail_queuesEmail_whenSubscriptionFound() {
        JSONObject payload = new JSONObject(
                "{\"payment\":{\"entity\":{\"id\":\"pay_r\",\"order_id\":\"order_r\",\"amount\":10000,\"currency\":\"INR\"}}}");
        Subscription sub = Subscription.builder()
                .id(30L).userId(3).plan("PREMIUM").status("PENDING")
                .userEmail("receipt@test.com").razorpayOrderId("order_r").build();

        when(paymentRepo.findByRazorpayPaymentId("pay_r")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByRazorpayOrderId("order_r")).thenReturn(Optional.of(sub));
        // findById is called inside sendReceiptEmail
        when(subscriptionRepo.findById(30L)).thenReturn(Optional.of(sub));

        svc.handleWebhookEvent("payment.captured", payload);

        verify(redis).convertAndSend(eq("email:send"), anyString());
    }

    // ── recordPayment — fallback to notes when no orderId matches ─────────────

    @Test
    void recordPayment_noOrderId_fallsBackToNotes() {
        // Webhook entity with no order_id — must look up via notes.userId
        JSONObject payload = new JSONObject(
                "{\"payment\":{\"entity\":{\"id\":\"pay_n\",\"amount\":10000,\"currency\":\"INR\"," +
                "\"notes\":{\"userId\":7,\"plan\":\"PREMIUM\"}}}}");
        Subscription sub = Subscription.builder()
                .id(40L).userId(7).plan("PENDING").status("PENDING").build();

        when(paymentRepo.findByRazorpayPaymentId("pay_n")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByUserId(7)).thenReturn(Optional.of(sub));

        svc.handleWebhookEvent("payment.failed", payload);

        // payment saved with the sub's ID resolved via notes
        verify(paymentRepo).save(argThat(p -> "FAILED".equals(p.getStatus()) && Long.valueOf(40L).equals(p.getSubscriptionId())));
    }

    // ── cancelSubscription ────────────────────────────────────────────────────

    @Test
    void cancelSubscription_activeSubscription_setsCancelled() {
        Subscription sub = Subscription.builder()
                .id(1L).userId(1).plan("PREMIUM").status("ACTIVE")
                .endDate(java.time.LocalDateTime.now().plusMonths(1)).build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(sub));

        svc.cancelSubscription(1);

        assertEquals("CANCELLED", sub.getStatus());
        verify(subscriptionRepo).save(sub);
    }

    @Test
    void cancelSubscription_freePlan_throwsException() {
        Subscription sub = Subscription.builder()
                .id(1L).userId(1).plan("FREE").status("ACTIVE").build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(sub));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> svc.cancelSubscription(1));
    }

    @Test
    void cancelSubscription_noSubscription_throwsException() {
        when(subscriptionRepo.findByUserId(99)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> svc.cancelSubscription(99));
    }
}
