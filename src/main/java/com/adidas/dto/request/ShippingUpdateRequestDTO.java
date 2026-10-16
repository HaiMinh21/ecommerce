package com.adidas.dto.request;

import com.adidas.entity.ShippingStatus;

public class ShippingUpdateRequestDTO {
    private String shippingProvider;
    private String trackingNumber;
    private ShippingStatus status;
}
