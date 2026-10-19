package com.adidas.repository;

import com.adidas.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ICategoryRepository extends JpaRepository<Category, Long> {
    boolean existsBySlug(String slug);
    List<Category> findByParentIsNull();
    List<Category> findByParentId(Long parentId);
    // Dùng cho Update: Kiểm tra xem slug đã có ai dùng chưa (ngoại trừ chính danh mục đang sửa)
    boolean existsBySlugAndIdNot(String slug, Long id);

    // Dùng cho Delete: Kiểm tra xem danh mục này có đang làm cha của ai không
    boolean existsByParentId(Long parentId);
}
