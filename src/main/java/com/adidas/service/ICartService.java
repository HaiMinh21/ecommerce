package com.adidas.service;

import com.adidas.dto.request.CartResponseDTO;
import org.springframework.stereotype.Service;

@Service
public interface ICartService {
    CartResponseDTO getCartByUserId(Long userId);
}
