package com.pickleball.order.controller;

import com.pickleball.order.dto.OrderResponse;
import com.pickleball.order.service.OrderService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final OrderService orders;

    public AdminOrderController(OrderService orders) { this.orders = orders; }

    @GetMapping
    public List<OrderResponse> list() { return orders.adminList(); }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable("id") Long id) { return orders.adminDetail(id); }

    @PostMapping("/{id}/{action:confirm|prepare|ready-for-pickup|ship|complete|cancel}")
    public OrderResponse action(@PathVariable("id") Long id, @PathVariable("action") String action) {
        return orders.adminAction(id, action);
    }

    @PostMapping("/{id}/assign")
    public OrderResponse assign(@PathVariable("id") Long id, @RequestBody AssignRequest request) {
        return orders.assign(id, request.username());
    }

    public record AssignRequest(String username) {}
}
