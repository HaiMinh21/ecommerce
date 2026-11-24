package com.adidas.dto.response;

import com.adidas.entity.Address;

public record AddressResponseDTO(Long id, Long userId, String receiverName, String phone,
                                 String addressLine, String ward, String district, String city,
                                 Boolean isDefault) {
    public static AddressResponseDTO from(Address address) {
        return new AddressResponseDTO(address.getId(), address.getUser().getId(),
                address.getReceiverName(), address.getPhone(), address.getAddressLine(),
                address.getWard(), address.getDistrict(), address.getCity(), address.getIsDefault());
    }
}
