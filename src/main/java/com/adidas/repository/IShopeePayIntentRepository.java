package com.adidas.repository;

import com.adidas.entity.ShopeePayIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IShopeePayIntentRepository extends JpaRepository<ShopeePayIntent, Long> {
    @Query("select i.orderId from ShopeePayIntent i where i.reference = :reference")
    Optional<Long> findOrderIdByReference(String reference);
}
