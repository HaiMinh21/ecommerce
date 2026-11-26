package com.adidas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "shopeepay_intents")
@Getter
@Setter
@NoArgsConstructor
public class ShopeePayIntent {
    public enum State { CREATING, READY, UNKNOWN, PAID, CLOSED }

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Column(nullable = false)
    private long providerAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant qrUrlExpiresAt;
    private Instant lastCheckedAt;

    @Column(length = 4096)
    private String qrContent;

    @Column(length = 4096)
    private String qrUrl;

    @Column(unique = true, length = 255)
    private String providerTransactionId;
}
