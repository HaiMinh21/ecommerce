package com.adidas.repository;

import com.adidas.entity.ProductSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IProductSaleRepository extends JpaRepository<ProductSale, Long> {
    List<ProductSale> findBySaleId(Long saleId);
    List<ProductSale> findByProductId(Long productId);
    Optional<ProductSale> findByProductIdAndSaleId(Long productId, Long saleId);
    void deleteByProductIdAndSaleId(Long productId, Long saleId);
}
