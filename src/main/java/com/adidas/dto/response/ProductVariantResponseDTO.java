package com.adidas.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantResponseDTO {
    private Long id;
    private String sku;
    private String variantName;
    private BigDecimal price;
    private Integer stockQuantity;
}
