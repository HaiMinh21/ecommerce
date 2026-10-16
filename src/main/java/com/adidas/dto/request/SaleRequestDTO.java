package com.adidas.dto.request;

import com.adidas.entity.DiscountType;
import com.adidas.entity.SaleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleRequestDTO {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private DiscountType discountType;
    @NotNull
    private BigDecimal discountValue;
    @NotNull
    private LocalDateTime startAt;
    @NotNull
    private LocalDateTime endAt;

    private SaleStatus status;
    private List<Long> productIds;
}
