package com.adidas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shopeepay_requests", uniqueConstraints =
        @UniqueConstraint(name = "uq_shopeepay_user_request", columnNames = {"user_id", "request_key"}))
@Getter
@NoArgsConstructor
public class ShopeePayRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_key", nullable = false, length = 36)
    private String requestKey;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    public ShopeePayRequest(Long userId, String requestKey, Long orderId) {
        this.userId = userId;
        this.requestKey = requestKey;
        this.orderId = orderId;
    }
}
