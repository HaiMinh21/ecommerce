package com.adidas.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistResponseDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productSlug;
    private BigDecimal productPrice;
    private String mainImageUrl;
    private LocalDateTime addedAt;
}
