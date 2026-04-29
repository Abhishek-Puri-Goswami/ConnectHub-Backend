package com.connecthub.payment.repository;

import com.connecthub.payment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Integer userId);

    Optional<Subscription> findByRazorpayOrderId(String razorpayOrderId);

    boolean existsByUserId(Integer userId);

    void deleteByUserId(Integer userId);

    List<Subscription> findAllByStatusAndEndDateBefore(String status, LocalDateTime dateTime);
}
