package com.adidas.controller;

import com.adidas.dto.request.AddressRequestDTO;
import com.adidas.dto.response.AddressResponseDTO;
import com.adidas.service.IAddressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/addresses")
@PreAuthorize("#userId == principal.user.id")
public class AddressController {
    private final IAddressService service;

    public AddressController(IAddressService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponseDTO>> getAddresses(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getAddresses(userId));
    }

    @GetMapping("/{addressId}")
    public ResponseEntity<AddressResponseDTO> getAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        return ResponseEntity.ok(service.getAddress(userId, addressId));
    }

    @PostMapping
    public ResponseEntity<AddressResponseDTO> createAddress(@PathVariable Long userId,
                                                            @Valid @RequestBody AddressRequestDTO request) {
        AddressResponseDTO result = service.createAddress(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + userId + "/addresses/" + result.id())).body(result);
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponseDTO> updateAddress(@PathVariable Long userId, @PathVariable Long addressId,
                                                            @Valid @RequestBody AddressRequestDTO request) {
        return ResponseEntity.ok(service.updateAddress(userId, addressId, request));
    }

    @PatchMapping("/{addressId}/default")
    public ResponseEntity<AddressResponseDTO> setDefaultAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        return ResponseEntity.ok(service.setDefaultAddress(userId, addressId));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        service.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }
}
