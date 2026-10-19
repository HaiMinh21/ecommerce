package com.adidas.service;

import com.adidas.dto.request.CategoryRequestDTO;
import com.adidas.dto.response.CategoryResponseDTO;
import com.adidas.entity.Category;
import com.adidas.exception.BadRequestException;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.ICategoryRepository;
import com.adidas.repository.IProductRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService implements ICategoryService{

    private ICategoryRepository categoryRepository;
    private final IProductRepository productRepository;
    private final ModelMapper modelMapper;

    public CategoryService(IProductRepository productRepository, ModelMapper modelMapper, ICategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.modelMapper = modelMapper;
        this.categoryRepository = categoryRepository;
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
            Category categoryParent = categoryRepository.findById(categoryRequestDTO.getParentId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục có id là: " + categoryRequestDTO.getParentId()));
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

    @Override
    @Transactional
    public CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO categoryRequestDTO) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục có id: " + id));
        if (categoryRepository.existsBySlugAndIdNot(categoryRequestDTO.getSlug(), id)) {
            throw new BadRequestException("Đường dẫn danh mục đã tồn tại!");
        }
        if (categoryRequestDTO.getParentId() != null) {
            if (categoryRequestDTO.getParentId().equals(id)) {
                throw new BadRequestException("Danh mục không thể làm cha của chính mình");
            }
            Category parent = categoryRepository.findById(categoryRequestDTO.getParentId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục cha"));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        category.setName(categoryRequestDTO.getName());
        category.setSlug(categoryRequestDTO.getSlug());
        category.setDescription(categoryRequestDTO.getDescription());

        Category updateCategory = categoryRepository.save(category);
        return mapToDTO(updateCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dang mục sản phẩm có id là: " + id));
        if (categoryRepository.existsByParentId(id)) {
            throw new BadRequestException("Không thể xóa! vui lòng xóa hoặc di chuyển danh mục con trước");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new BadRequestException("Không thể xóa danh mục đang có sản phẩm");
        }

        categoryRepository.delete(category);
    }
}
