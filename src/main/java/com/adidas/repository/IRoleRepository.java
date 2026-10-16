package com.adidas.repository;

import com.adidas.entity.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IRoleRepository extends JpaRepository<Roles, Long> {
    Optional<Roles> findByName(String name);

    // Kiểm tra xem trong bẳng Role đã tồn tại name này hay chưa
    boolean existsByName(String name);
}
