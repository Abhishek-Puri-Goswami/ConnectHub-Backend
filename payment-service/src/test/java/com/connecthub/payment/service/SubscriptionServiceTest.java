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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        ReflectionTestUtils.setField(svc, "razorpayKeyId", "test_key");
        ReflectionTestUtils.setField(svc, "proAmountPaise", 9900L);
    }

    @Test
    void createOrder_existingActive_returnsExisting() {
        Subscription existing = Subscription.builder()
                .userId(1).status("ACTIVE").plan("PRO").razorpayOrderId("order_1").build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(existing));

        SubscriptionResponse res = svc.createOrder(1, "test@test.com");

        assertEquals("order_1", res.getRazorpayOrderId());
        verify(subscriptionRepo, never()).save(any());
    }

    @Test
    void handleWebhookEvent_paymentCaptured_activatesAndRecords() {
        JSONObject payload = new JSONObject(
                "{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":9900,\"currency\":\"INR\"}}}");
        Subscription existing = Subscription.builder()
                .id(10L).userId(1).plan("PRO").status("PENDING").razorpayOrderId("order_1").build();

        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(existing));

        svc.handleWebhookEvent("payment.captured", payload);

        assertEquals("ACTIVE", existing.getStatus());
        assertEquals("PRO", existing.getPlan());
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
                "{\"payment\":{\"entity\":{\"id\":\"pay_2\",\"order_id\":\"order_1\",\"amount\":9900,\"currency\":\"INR\"}}}");

        when(paymentRepo.findByRazorpayPaymentId("pay_2")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByRazorpayOrderId("order_1")).thenReturn(Optional.empty());

        svc.handleWebhookEvent("payment.failed", payload);

        verify(paymentRepo).save(argThat(p -> "FAILED".equals(p.getStatus())));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void handleWebhookEvent_idempotent_skipsExistingPayment() {
        JSONObject payload = new JSONObject(
                "{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":9900,\"currency\":\"INR\"}}}");

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
}
