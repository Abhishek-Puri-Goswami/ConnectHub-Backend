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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private RazorpayClient razorpayClient;
    @Mock private SubscriptionRepository subscriptionRepo;
    @Mock private PaymentRepository paymentRepo;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private SubscriptionService svc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(svc, "razorpayKeyId", "test_key");
    }

    @Test
    void createSubscription_existingActive_returnsExisting() {
        Subscription existing = Subscription.builder().userId(1).status("ACTIVE").plan("PRO").razorpaySubId("sub_1").build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(existing));

        SubscriptionResponse res = svc.createSubscription(1, "plan_1", 12, "test@test.com");

        assertEquals("sub_1", res.getRazorpaySubId());
        verify(subscriptionRepo, never()).save(any());
    }

    @Test
    void handleWebhookEvent_activated_upgradesExisting() {
        JSONObject payload = new JSONObject("{\"subscription\":{\"entity\":{\"id\":\"sub_1\"}}}");
        Subscription existing = new Subscription();
        existing.setUserId(1);
        existing.setRazorpaySubId("sub_1");
        
        when(subscriptionRepo.findAll()).thenReturn(java.util.List.of(existing));

        svc.handleWebhookEvent("subscription.activated", payload);

        assertEquals("ACTIVE", existing.getStatus());
        assertEquals("PRO", existing.getPlan());
        verify(subscriptionRepo).save(existing);
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("1"), anyString());
    }

    @Test
    void handleWebhookEvent_activated_createsNewFromNotes() {
        JSONObject payload = new JSONObject("{\"subscription\":{\"entity\":{\"id\":\"sub_new\", \"notes\":{\"userId\":2}}}}");
        when(subscriptionRepo.findAll()).thenReturn(java.util.List.of());

        svc.handleWebhookEvent("subscription.activated", payload);

        verify(subscriptionRepo).save(argThat(sub -> sub.getUserId() == 2 && "PRO".equals(sub.getPlan())));
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("2"), anyString());
    }

    @Test
    void handleWebhookEvent_cancelled_cancelsExisting() {
        JSONObject payload = new JSONObject("{\"subscription\":{\"entity\":{\"id\":\"sub_1\"}}}");
        Subscription existing = new Subscription();
        existing.setUserId(1);
        existing.setRazorpaySubId("sub_1");
        
        when(subscriptionRepo.findAll()).thenReturn(java.util.List.of(existing));

        svc.handleWebhookEvent("subscription.cancelled", payload);

        assertEquals("CANCELLED", existing.getStatus());
        assertEquals("FREE", existing.getPlan());
        assertNotNull(existing.getEndDate());
        verify(subscriptionRepo).save(existing);
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("1"), anyString());
    }

    @Test
    void handleWebhookEvent_paymentCaptured_recordsPayment() {
        JSONObject payload = new JSONObject("{\"payment\":{\"entity\":{\"id\":\"pay_1\", \"amount\":10000, \"currency\":\"INR\", \"subscription_id\":\"sub_1\"}}}");
        Subscription existing = Subscription.builder().id(10L).razorpaySubId("sub_1").build();
        
        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        when(subscriptionRepo.findAll()).thenReturn(java.util.List.of(existing));

        svc.handleWebhookEvent("payment.captured", payload);

        verify(paymentRepo).save(argThat(p -> 
            "pay_1".equals(p.getRazorpayPaymentId()) && 
            "CAPTURED".equals(p.getStatus()) &&
            10L == p.getSubscriptionId()
        ));
    }
}
