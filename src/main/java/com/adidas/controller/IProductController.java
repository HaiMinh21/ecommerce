package com.adidas.controller;

import com.adidas.dto.request.ProductRequestDTO;
import com.adidas.dto.response.ProductResponseDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public interface IProductController {

    ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO productRequestDTO);

    ResponseEntity<ProductResponseDTO> getProductById(@PathVariable Long id);

    ResponseEntity<Page<ProductResponseDTO>> getAllProducts(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long categoryId);
}
