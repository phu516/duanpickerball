package com.pickleball.product.repository;

import com.pickleball.product.entity.ProductFulfillmentOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFulfillmentRepository extends JpaRepository<ProductFulfillmentOption, Long> {
    List<ProductFulfillmentOption> findByProductId(Long productId);
    List<ProductFulfillmentOption> findByProductIdIn(List<Long> productIds);
    boolean existsByProductIdAndMethod(Long productId, String method);
    void deleteByProductId(Long productId);
}
