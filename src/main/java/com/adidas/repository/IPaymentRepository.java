package com.adidas.repository;

import com.adidas.entity.Payment;
import com.adidas.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IPaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndPaymentMethod(Long orderId, PaymentMethod paymentMethod);

    boolean existsByPaymentMethodAndTransactionId(PaymentMethod paymentMethod, String transactionId);

    // đối soát với cổng thanh toán (VNPAY/ MOMO) khi gọi webhook tả kết quả
    Optional<Payment> findByTransactionId(String transactionId);
}
