package com.adidas.controller;

import com.adidas.dto.request.ProductRequestDTO;
import com.adidas.dto.response.ProductResponseDTO;
import com.adidas.service.IProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/products")
public class ProductController implements IProductController{

    @Autowired
    private IProductService productService;

    @Override
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(ProductRequestDTO productRequestDTO) {
        ProductResponseDTO productResponseDTO = productService.createProduct(productRequestDTO);
        return new ResponseEntity<>(productResponseDTO, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ProductResponseDTO> getProductById(Long id) {
        ProductResponseDTO productResponseDTO = productService.getProductById(id);
        return new ResponseEntity<>(productResponseDTO, HttpStatus.ACCEPTED);
    }

    @Override
    @GetMapping
    public ResponseEntity<Page<ProductResponseDTO>> getAllProducts(int page, int size, String keyword, Long categoryId) {
        Page<ProductResponseDTO> productResponseDTO = productService.getAllProducts(page, size, keyword, categoryId);
        return new ResponseEntity<>(productResponseDTO, HttpStatus.ACCEPTED);
    }
}
