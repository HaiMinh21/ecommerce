package com.adidas.dto.request;

import com.adidas.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponRequestDTO {
    @NotBlank
    private String code;
    private String description;
    @NotNull
    private DiscountType discountType;
    @NotNull
    private BigDecimal discountValue;

    private BigDecimal minOrderValue;
    private BigDecimal maxDiscount;
    private Integer usageLimit;
    @NotNull
    private LocalDateTime startAt;
    @NotNull
    private LocalDateTime endAt;
}
