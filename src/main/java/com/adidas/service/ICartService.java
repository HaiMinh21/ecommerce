package com.adidas.service;

import com.adidas.dto.request.CartItemRequestDTO;
import com.adidas.dto.request.CartRequestDTO;
import com.adidas.dto.response.CartResponseDTO;
import org.springframework.stereotype.Service;

@Service
public interface ICartService {

    CartResponseDTO getCartByUserId(Long userId);

    CartResponseDTO addToCart (Long userId, CartItemRequestDTO cartItemRequestDTO);

}
