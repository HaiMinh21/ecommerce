package com.adidas.dto.request;

import com.adidas.entity.ShippingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShippingUpdateRequestDTO(
        @NotBlank @Size(max = 100) String shippingProvider,
        @NotBlank @Size(max = 100) String trackingNumber,
        @NotNull ShippingStatus status) {
}
