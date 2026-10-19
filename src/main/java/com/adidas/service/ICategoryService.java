package com.adidas.service;

import com.adidas.dto.request.CategoryRequestDTO;
import com.adidas.dto.response.CategoryResponseDTO;
import com.adidas.entity.Category;

import java.util.List;

public interface ICategoryService {
    CategoryResponseDTO createCategory(CategoryRequestDTO categoryRequestDTO);

    List<CategoryResponseDTO> getCategoryTree();

    CategoryResponseDTO mapToDTO(Category category);

    CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO categoryRequestDTO);

    void deleteCategory(Long id);
}
