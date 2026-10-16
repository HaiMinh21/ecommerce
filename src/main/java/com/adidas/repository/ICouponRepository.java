package com.adidas.repository;

import com.adidas.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ICouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);
    Optional<Coupon> findByCodeAndStartAtBeforeAndEndAtAfter(String code, LocalDateTime nowForStart, LocalDateTime nowForEnd);
}
