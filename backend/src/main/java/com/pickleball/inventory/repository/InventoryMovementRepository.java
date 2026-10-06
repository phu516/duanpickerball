package com.pickleball.inventory.repository;

import com.pickleball.inventory.entity.InventoryMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findByOrderByCreatedAtDesc();
}
