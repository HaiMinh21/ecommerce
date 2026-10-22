package com.adidas.controller;

import com.adidas.dto.request.CartItemRequestDTO;
import com.adidas.dto.request.CartRequestDTO;
import com.adidas.dto.response.CartResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

public interface ICartController {

    ResponseEntity<CartResponseDTO> getCartByUserId(@PathVariable Long userId);

    ResponseEntity<CartResponseDTO> addToCart (@PathVariable Long userId, @Valid @RequestBody CartItemRequestDTO cartItemRequestDTO);

    ResponseEntity<CartResponseDTO> updateCartItemQuantity(@PathVariable Long userId, @PathVariable Long cartItemId, @RequestParam Integer quantity);

    ResponseEntity<CartResponseDTO> removeCartItem(@PathVariable Long userId, @PathVariable Long cartItemId);
}
