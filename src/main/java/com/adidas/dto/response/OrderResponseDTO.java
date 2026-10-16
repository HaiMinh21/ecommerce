package com.adidas.dto.response;

import com.adidas.entity.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDTO {
    private Long id;
    private String orderCode;
    private Long userId;
    private String receiverName;
    private String phone;
    private String deliveryAddress;

    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;

    private OrderStatus status;
    private String note;
    private LocalDateTime createdAt;

    private List<OrderItemResponseDTO> items;
    private PaymentResponseDTO payment;
}
