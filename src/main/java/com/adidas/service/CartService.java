package com.adidas.service;

import com.adidas.dto.request.CartResponseDTO;
import com.adidas.entity.Cart;
import com.adidas.repository.ICartRepository;
import com.adidas.repository.IProductImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service
public class CartService implements ICartService{

    private final ICartRepository cartRepository;

    private final IProductImageRepository productImageRepository;

    public CartService(ICartRepository cartRepository, IProductImageRepository productImageRepository) {
        this.cartRepository = cartRepository;
        this.productImageRepository = productImageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDTO getCartByUserId(Long userId) {

        return null;
    }
}
