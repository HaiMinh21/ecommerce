package com.adidas.service;

import com.adidas.dto.request.ProductRequestDTO;
import com.adidas.dto.response.ProductResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public interface IProductService {
    ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO);
    ProductResponseDTO getProductById(Long id);
    Page<ProductResponseDTO> getAllProducts(int page , int size, String keyword, Long categoryId);
    ProductResponseDTO updateProduct(Long id, ProductRequestDTO productRequestDTO);
    void deleteByProductId(Long id);
}
