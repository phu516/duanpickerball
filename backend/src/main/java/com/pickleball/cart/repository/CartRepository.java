package com.pickleball.cart.repository;

import com.pickleball.cart.entity.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByAccessHash(String accessHash);
}
