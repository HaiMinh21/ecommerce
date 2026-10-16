package com.adidas.dto.request;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCallbackRequestDTO {
    private String orderCode;
    private String transactionId;
    private BigDecimal amount;
    private String responseCode;
    private String paymentProvider;
}
