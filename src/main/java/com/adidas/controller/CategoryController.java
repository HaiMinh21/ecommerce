package com.adidas.controller;

import com.adidas.dto.request.CategoryRequestDTO;
import com.adidas.dto.response.CategoryResponseDTO;
import com.adidas.service.ICategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/category")
public class CategoryController implements ICategoryController{
    @Autowired
    private ICategoryService categoryService;

    @Override
    @PostMapping
    public ResponseEntity<CategoryResponseDTO> createCategory(CategoryRequestDTO categoryRequestDTO) {
        CategoryResponseDTO categoryResponseDTO = categoryService.createCategory(categoryRequestDTO);
        return new ResponseEntity<>(categoryResponseDTO, HttpStatus.CREATED);
    }

    @Override
    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getCategoryTree() {
        List<CategoryResponseDTO> categories = categoryService.getCategoryTree();
        return ResponseEntity.ok(categories);
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> updateCategory(Long id, CategoryRequestDTO categoryRequestDTO) {
        CategoryResponseDTO updateCategory = categoryService.updateCategory(id, categoryRequestDTO);
        return new ResponseEntity<>(updateCategory, HttpStatus.OK);
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
