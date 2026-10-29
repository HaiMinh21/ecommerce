package com.adidas.repository;

import com.adidas.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IOrderItemRepository extends JpaRepository<OrderItem , Long> {
    // lấy toàn bộ mặt hàng thuộc một đơn hàng cụ thể
    List<OrderItem> findByOrderId(Long orderId);
}
