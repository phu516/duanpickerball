package com.pickleball.inventory.repository;

import com.pickleball.inventory.entity.InventoryReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByOrderItemIdIn(List<Long> itemIds);
}
