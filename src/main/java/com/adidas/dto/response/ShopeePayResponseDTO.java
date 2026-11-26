package com.adidas.dto.response;

import com.adidas.entity.PaymentStatus;
import com.adidas.entity.ShopeePayIntent;

import java.math.BigDecimal;
import java.time.Instant;

public record ShopeePayResponseDTO(
        Long orderId,
        String reference,
        BigDecimal amount,
        String currency,
        PaymentStatus paymentStatus,
        ShopeePayIntent.State state,
        Instant expiresAt,
        String qrContent,
        String qrUrl,
        boolean qrExpired) {
}
