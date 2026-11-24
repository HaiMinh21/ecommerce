package com.adidas.service;

import com.adidas.dto.request.AddressRequestDTO;
import com.adidas.dto.response.AddressResponseDTO;
import java.util.List;

public interface IAddressService {
    List<AddressResponseDTO> getAddresses(Long userId);
    AddressResponseDTO getAddress(Long userId, Long addressId);
    AddressResponseDTO createAddress(Long userId, AddressRequestDTO request);
    AddressResponseDTO updateAddress(Long userId, Long addressId, AddressRequestDTO request);
    AddressResponseDTO setDefaultAddress(Long userId, Long addressId);
    void deleteAddress(Long userId, Long addressId);
}
