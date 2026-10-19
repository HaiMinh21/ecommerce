package com.adidas.controller;

import com.adidas.dto.request.CategoryRequestDTO;
import com.adidas.dto.response.CategoryResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface ICategoryController {
    ResponseEntity<CategoryResponseDTO> createCategory(@Valid @RequestBody CategoryRequestDTO categoryRequestDTO);
    ResponseEntity<List<CategoryResponseDTO>> getCategoryTree();
    ResponseEntity<CategoryResponseDTO> updateCategory(@PathVariable Long id, @RequestBody CategoryRequestDTO categoryRequestDTO);
    ResponseEntity<Void> deleteCategory(@PathVariable Long id);
}
