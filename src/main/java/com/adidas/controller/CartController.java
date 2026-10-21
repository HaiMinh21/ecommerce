package com.adidas.controller;

import com.adidas.dto.request.CartItemRequestDTO;
import com.adidas.dto.request.CartRequestDTO;
import com.adidas.dto.response.CartResponseDTO;
import com.adidas.service.ICartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/carts")
public class CartController implements ICartController {

    private final ICartService cartService;

    public CartController(ICartService cartService) {
        this.cartService = cartService;
    }

    @Override
    @GetMapping("/users/{userId}")
    public ResponseEntity<CartResponseDTO> getCartByUserId(@PathVariable Long userId) {
        CartResponseDTO cartResponseDTO = cartService.getCartByUserId(userId);
        return ResponseEntity.ok(cartResponseDTO);
    }

    @Override
    @PostMapping("/users/{userId}")
    public ResponseEntity<CartResponseDTO> addToCart(@PathVariable Long userId,@Valid @RequestBody CartItemRequestDTO cartItemRequestDTO) {
        CartResponseDTO cartResponseDTO = cartService.addToCart(userId, cartItemRequestDTO);
        return ResponseEntity.ok(cartResponseDTO);
    }
}
