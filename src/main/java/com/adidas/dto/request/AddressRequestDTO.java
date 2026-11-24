package com.adidas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequestDTO(
        @NotBlank @Size(max = 150) String receiverName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Phone must contain 9 to 15 digits") String phone,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 255) String ward,
        @NotBlank @Size(max = 255) String district,
        @NotBlank @Size(max = 255) String city,
        Boolean isDefault) {
}
