package com.adidas.repository;

import com.adidas.entity.UserStatus;
import com.adidas.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IUserRepository extends JpaRepository<Users, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Users u where u.id = :id")
    Optional<Users> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "role")
    Optional<Users> findByUsername(String username);
    Optional<Users> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<Users> findByStatus(UserStatus status);
}
