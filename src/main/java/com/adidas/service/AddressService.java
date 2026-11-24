package com.adidas.service;

import com.adidas.dto.request.AddressRequestDTO;
import com.adidas.dto.response.AddressResponseDTO;
import com.adidas.entity.Address;
import com.adidas.entity.Users;
import com.adidas.exception.BadRequestException;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.IAddressRepository;
import com.adidas.repository.IOrderRepository;
import com.adidas.repository.IUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class AddressService implements IAddressService {
    private final IAddressRepository addresses;
    private final IUserRepository users;
    private final IOrderRepository orders;

    public AddressService(IAddressRepository addresses, IUserRepository users, IOrderRepository orders) {
        this.addresses = addresses;
        this.users = users;
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponseDTO> getAddresses(Long userId) {
        if (!users.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
        return addresses.findByUserIdOrderByIsDefaultDescIdAsc(userId).stream()
                .map(AddressResponseDTO::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponseDTO getAddress(Long userId, Long addressId) {
        return AddressResponseDTO.from(ownedAddress(userId, addressId));
    }

    @Override
    public AddressResponseDTO createAddress(Long userId, AddressRequestDTO request) {
        Users user = lockUser(userId);
        List<Address> existing = addresses.findByUserIdOrderByIsDefaultDescIdAsc(userId);
        Address address = new Address();
        address.setUser(user);
        apply(address, request);
        address.setIsDefault(existing.isEmpty() || Boolean.TRUE.equals(request.isDefault()));
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            clearDefaults(existing);
        }
        return AddressResponseDTO.from(addresses.save(address));
    }

    @Override
    public AddressResponseDTO updateAddress(Long userId, Long addressId, AddressRequestDTO request) {
        lockUser(userId);
        Address address = ownedAddress(userId, addressId);
        ensureUnused(addressId);
        if (Boolean.TRUE.equals(address.getIsDefault()) && Boolean.FALSE.equals(request.isDefault())) {
            throw new BadRequestException("Choose another default address before removing this default");
        }
        apply(address, request);
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefaults(addresses.findByUserIdOrderByIsDefaultDescIdAsc(userId));
            address.setIsDefault(true);
        }
        return AddressResponseDTO.from(addresses.save(address));
    }

    @Override
    public AddressResponseDTO setDefaultAddress(Long userId, Long addressId) {
        lockUser(userId);
        Address address = ownedAddress(userId, addressId);
        clearDefaults(addresses.findByUserIdOrderByIsDefaultDescIdAsc(userId));
        address.setIsDefault(true);
        return AddressResponseDTO.from(addresses.save(address));
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        lockUser(userId);
        Address address = ownedAddress(userId, addressId);
        ensureUnused(addressId);
        addresses.delete(address);
        addresses.flush();
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            List<Address> remaining = addresses.findByUserIdOrderByIsDefaultDescIdAsc(userId);
            if (!remaining.isEmpty()) {
                clearDefaults(remaining);
                remaining.get(0).setIsDefault(true);
                addresses.save(remaining.get(0));
            }
        }
    }

    private Users lockUser(Long userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Address ownedAddress(Long userId, Long addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found for this user"));
    }

    private void ensureUnused(Long addressId) {
        if (orders.existsByAddressId(addressId)) {
            throw new BadRequestException("Address is used by an order. Create a new address instead");
        }
    }

    private void clearDefaults(List<Address> existing) {
        existing.forEach(address -> address.setIsDefault(false));
        addresses.saveAll(existing);
    }

    private void apply(Address address, AddressRequestDTO request) {
        address.setReceiverName(request.receiverName().trim());
        address.setPhone(request.phone().trim());
        address.setAddressLine(request.addressLine().trim());
        address.setWard(request.ward().trim());
        address.setDistrict(request.district().trim());
        address.setCity(request.city().trim());
    }
}
