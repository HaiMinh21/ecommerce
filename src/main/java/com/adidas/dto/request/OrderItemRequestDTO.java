package com.adidas.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequestDTO {
    @NotNull
    private Long productId;
    private Long variantId;
    @NotNull @Min(1)
    private Integer quantity;
}
