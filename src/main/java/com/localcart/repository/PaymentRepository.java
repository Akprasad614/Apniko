package com.localcart.repository;

import com.localcart.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);
}
