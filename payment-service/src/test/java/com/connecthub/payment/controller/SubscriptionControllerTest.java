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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    @Test
    void createSubscription() {
        CreateSubscriptionRequest req = new CreateSubscriptionRequest();
        req.setPlanId("plan_1");
        req.setTotalCount(12);
        
        SubscriptionResponse sr = SubscriptionResponse.builder().build();
        when(subscriptionService.createSubscription(1, "plan_1", 12, null)).thenReturn(sr);

        ResponseEntity<SubscriptionResponse> res = subscriptionController.createSubscription(1, null, req);
        
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(sr, res.getBody());
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
    void getPaymentHistory() {
        List<PaymentResponse> history = List.of(PaymentResponse.builder().build());
        when(subscriptionService.getPaymentHistory(1)).thenReturn(history);

        ResponseEntity<List<PaymentResponse>> res = subscriptionController.getPaymentHistory(1);
        
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(history, res.getBody());
    }
}
