package com.adidas.repository;

import com.adidas.dto.response.BestSellingProductResponseDTO;

import com.adidas.entity.ProductStatus;
import com.adidas.entity.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.adidas.entity.Products;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IProductRepository extends JpaRepository<Products, Long> {
    @Query(value = """
            select new com.adidas.dto.response.BestSellingProductResponseDTO(
                p.id, p.name, sum(oi.quantity))
            from OrderItem oi
            join oi.product p
            where oi.order.status = :status
            group by p.id, p.name
            order by sum(oi.quantity) desc, p.id asc
            """, countQuery = """
            select count(distinct oi.product.id)
            from OrderItem oi
            where oi.order.status = :status
            """)
    Page<BestSellingProductResponseDTO> findBestSellingProducts(
            @Param("status") OrderStatus status,
            Pageable pageable);

    Optional<Products> findBySlug(String slug);
    Optional<Products> findBySku(String sku);
    // kiểm tra xem đã tồn tại slug chưa
    boolean existsBySlug(String slug);
    boolean existsBySku(String sku);
    boolean existsByCategoryId(Long id);

    Page<Products> findByCategoryIdAndStatus(Long categoryId, ProductStatus status, Pageable pageable);
    Page<Products> findByStatus(ProductStatus status, Pageable pageable);

//  tìm kiếm sản phẩm theo tên không phân biệt chữ hoa, chữ thường và phân trang
    Page<Products> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

//  tìm kiếm theo danh mục phân trang
    Page<Products> findByCategoryId(Long categoryId, Pageable pageable);
}
