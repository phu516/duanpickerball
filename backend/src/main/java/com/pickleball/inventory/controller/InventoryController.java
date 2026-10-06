package com.pickleball.inventory.controller;

import com.pickleball.inventory.entity.InventoryMovement;
import com.pickleball.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) { this.inventory = inventory; }

    @GetMapping
    public List<InventoryService.InventoryView> overview() { return inventory.overview(); }

    @GetMapping("/movements")
    public List<InventoryMovement> movements() { return inventory.movements(); }

    @PostMapping("/adjustments")
    public InventoryService.InventoryView adjust(@Valid @RequestBody Adjustment request) {
        return inventory.adjust(request.variantId(), request.delta(), request.reason(), null);
    }

    public record Adjustment(Long variantId, int delta, @NotBlank String reason) {}
}
