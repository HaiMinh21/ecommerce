package com.adidas.repository;

import com.adidas.repository.projection.MonthlyRevenueProjection;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import com.adidas.entity.Order;
import com.adidas.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface IOrderRepository extends JpaRepository<Order, Long> {
    boolean existsByAddressId(Long addressId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT MONTH(o.deliveredAt) AS month, COUNT(o.id) AS totalOrders,
                   SUM(o.finalAmount) AS revenue
            FROM Order o
            WHERE o.status = :status
              AND o.deliveredAt >= :start AND o.deliveredAt < :end
            GROUP BY MONTH(o.deliveredAt)
            ORDER BY MONTH(o.deliveredAt)
            """)
    List<MonthlyRevenueProjection> calculateMonthlyRevenue(
            @Param("status") OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // tìm đơn hàng theo mã giao dịch
    Optional<Order> findByOrderCode(String orderCode);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    // lấy đơn hàng của user theo trạng thái: đang giao, đã hủy
    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus orderStatus, Pageable pageable);

    @Query("SELECT SUM(o.finalAmount) FROM Order o WHERE o.status = :status")
    BigDecimal calculateTotalRevenueByStatus(@Param("status") OrderStatus status);
}
