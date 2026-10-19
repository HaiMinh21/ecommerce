package com.adidas.service;

import com.adidas.dto.request.CategoryRequestDTO;
import com.adidas.dto.response.CategoryResponseDTO;
import com.adidas.entity.Category;
import com.adidas.exception.BadRequestException;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.ICategoryRepository;
import org.modelmapper.ModelMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

public class CategoryService implements ICategoryService{

    private ICategoryRepository categoryRepository;
    private final ModelMapper modelMapper;

    public CategoryService(ICategoryRepository categoryRepository, ModelMapper modelMapper) {
        this.categoryRepository = categoryRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional
    public CategoryResponseDTO createCategory(CategoryRequestDTO categoryRequestDTO) {
        if (categoryRepository.existsBySlug(categoryRequestDTO.getSlug())) {
            throw new BadRequestException("Đường dẫn danh mục sản phẩm đã tồn tại");
        }
        Category category = modelMapper.map(categoryRequestDTO, Category.class);

        // Người dùng truyền parentId để tạo danh mục cấp 2, cấp 3
        if (categoryRequestDTO.getParentId() != null) {
            Category categoryParent = categoryRepository.findById(category.getId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục có id là: " + categoryRequestDTO.getParentId()));
            category.setParent(categoryParent);
        }
        Category savedCategory = categoryRepository.save(category);
        return mapToDTO(savedCategory);
    }

    @Override
    public List<CategoryResponseDTO> getCategoryTree() {
        // Chỉ lấy các danh mục Cấp 1 (Gốc) từ database
        List<Category> rootCategories = categoryRepository.findByParentIsNull();

        // Hàm mapToDTO bên dưới sẽ tự động đệ quy lấy tiếp Cấp 2, Cấp 3...
        return rootCategories.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponseDTO mapToDTO(Category category) {
        CategoryResponseDTO dto = modelMapper.map(category, CategoryResponseDTO.class);

        if (category.getParent() != null) {
            dto.setParentId(category.getParent().getId());
        }

        // Đệ quy: Lặp qua các danh mục con và map chúng sang DTO
        if (category.getSubCategories() != null && !category.getSubCategories().isEmpty()) {
            List<CategoryResponseDTO> childrenDTOs = category.getSubCategories().stream()
                    .map(this::mapToDTO) // Tự gọi lại chính hàm này cho các nhánh con
                    .collect(Collectors.toList());
            dto.setSubCategories(childrenDTOs);
        }

        return dto;
    }
}
