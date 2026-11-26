package com.adidas.repository;

import com.adidas.entity.ShopeePayRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IShopeePayRequestRepository extends JpaRepository<ShopeePayRequest, Long> {
    Optional<ShopeePayRequest> findByUserIdAndRequestKey(Long userId, String requestKey);
}
