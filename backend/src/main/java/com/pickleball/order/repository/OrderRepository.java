package com.pickleball.order.repository;

import com.pickleball.order.entity.StoreOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<StoreOrder, Long> {
    Optional<StoreOrder> findByOrderCode(String orderCode);
    List<StoreOrder> findByAssignedStaffUsernameAndFulfillmentMethod(String username, String method);
    List<StoreOrder> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);
}
