package com.adidas.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToCartRequestDTO {
    @NotNull(message = "Thiếu ID sản phẩm")
    private Long productId;
    @NotNull(message = "Thiếu số lượng")
    @Min(1)
    private Integer quantity;
}
