package com.pickleball.product.repository;

import com.pickleball.product.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderByPrimaryImageDescSortOrderAscIdAsc(Long productId);
    @Query("select i from ProductImage i where i.product.id in :productIds "
            + "order by i.product.id, i.primaryImage desc, i.sortOrder asc, i.id asc")
    List<ProductImage> findForProducts(@Param("productIds") List<Long> productIds);
}
