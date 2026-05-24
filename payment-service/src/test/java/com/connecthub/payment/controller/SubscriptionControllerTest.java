package com.connecthub.payment.controller;

import com.connecthub.payment.dto.CreateSubscriptionRequest;
import com.connecthub.payment.dto.PaymentResponse;
import com.connecthub.payment.dto.SubscriptionResponse;
import com.connecthub.payment.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    @Test
    void createOrder_premium_returnsOk() {
        SubscriptionResponse sr = SubscriptionResponse.builder().plan("PREMIUM").build();
        when(subscriptionService.createOrder(1, null, "PREMIUM")).thenReturn(sr);

        CreateSubscriptionRequest req = new CreateSubscriptionRequest();
        req.setPlan("PREMIUM");
        ResponseEntity<SubscriptionResponse> res = subscriptionController.createSubscription(1, null, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(sr, res.getBody());
        verify(subscriptionService).createOrder(eq(1), eq(null), eq("PREMIUM"));
    }

    @Test
    void createOrder_platinum_passesCorrectPlan() {
        SubscriptionResponse sr = SubscriptionResponse.builder().plan("PLATINUM").build();
        when(subscriptionService.createOrder(1, "u@example.com", "PLATINUM")).thenReturn(sr);

        CreateSubscriptionRequest req = new CreateSubscriptionRequest();
        req.setPlan("PLATINUM");
        ResponseEntity<SubscriptionResponse> res = subscriptionController.createSubscription(1, "u@example.com", req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(subscriptionService).createOrder(eq(1), eq("u@example.com"), eq("PLATINUM"));
    }

    @Test
    void cancelSubscription_returnsOk() {
        doNothing().when(subscriptionService).cancelSubscription(1);

        ResponseEntity<Void> res = subscriptionController.cancelSubscription(1);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(subscriptionService).cancelSubscription(1);
    }

    @Test
    void getConfig_returnsOk() {
        Map<String, Object> cfg = Map.of(
                "razorpayKeyId", "key",
                "premiumAmountPaise", 10000L,
                "platinumAmountPaise", 14900L);
        when(subscriptionService.getConfig()).thenReturn(cfg);

        ResponseEntity<Map<String, Object>> res = subscriptionController.getConfig();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(cfg, res.getBody());
    }

    @Test
    void getStatus_found() {
        SubscriptionResponse sr = SubscriptionResponse.builder().build();
        when(subscriptionService.getSubscription(1)).thenReturn(Optional.of(sr));

        ResponseEntity<SubscriptionResponse> res = subscriptionController.getStatus(1);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(sr, res.getBody());
    }

    @Test
    void getStatus_notFound() {
        when(subscriptionService.getSubscription(1)).thenReturn(Optional.empty());

        ResponseEntity<SubscriptionResponse> res = subscriptionController.getStatus(1);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void getPaymentHistory_returnsOk() {
        List<PaymentResponse> history = List.of(PaymentResponse.builder().build());
        when(subscriptionService.getPaymentHistory(1)).thenReturn(history);

        ResponseEntity<List<PaymentResponse>> res = subscriptionController.getPaymentHistory(1);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(history, res.getBody());
    }
}
