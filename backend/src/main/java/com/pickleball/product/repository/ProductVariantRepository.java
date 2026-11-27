package com.pickleball.product.repository;

import com.pickleball.product.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);
    List<ProductVariant> findByProductIdInOrderByIdAsc(List<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id")
    ProductVariant findLocked(@Param("id") Long id);
}
