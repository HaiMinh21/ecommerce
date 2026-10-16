package com.adidas.repository;

import com.adidas.entity.Sale;
import com.adidas.entity.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ISaleRepository extends JpaRepository<Sale, Long> {
//    Lấy danh sách các đợt sale đang diễn ra
    List<Sale> findByStatusAndStartAtBeforeAndEndAtAfter(SaleStatus status, LocalDateTime nowForStart, LocalDateTime nowForEnd);
}
