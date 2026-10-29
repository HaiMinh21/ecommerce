package com.adidas.repository;

import com.adidas.entity.Order;
import com.adidas.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IOrderRepository extends JpaRepository<Order, Long> {

    // tìm đơn hàng theo mã giao dịch
    Optional<Order> findByOrderCode(String orderCode);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    // lấy đơn hàng của user theo trạng thái: đang giao, đã hủy
    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);
}
