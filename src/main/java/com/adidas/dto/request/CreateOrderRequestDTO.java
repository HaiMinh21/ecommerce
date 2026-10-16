package com.adidas.dto.request;

import com.adidas.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequestDTO {
    @NotNull(message = "Thiếu địa chỉ giao hàng")
    private Long addressId;
    private String couponCode;
    @NotNull(message = "Thiếu phương thức thanh toán")
    private PaymentMethod paymentMethod;
    private String note;
    private List<OrderItemRequestDTO> items;
}
