package com.adidas.dto.response;

import com.adidas.entity.ShippingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingResponseDTO {
    private Long id;
    private Long orderId;
    private String shippingProvider;
    private String trackingNumber;
    private BigDecimal shippingFee;
    private ShippingStatus status;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
}
