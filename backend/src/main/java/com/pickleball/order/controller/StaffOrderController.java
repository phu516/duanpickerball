package com.pickleball.order.controller;

import com.pickleball.order.dto.OrderResponse;
import com.pickleball.order.service.OrderService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffOrderController {
    private final OrderService orders;

    public StaffOrderController(OrderService orders) { this.orders = orders; }

    @GetMapping("/fulfillments")
    public List<OrderResponse> list(Principal principal) { return orders.staffOrders(principal.getName()); }

    @PostMapping("/orders/{id}/{action:start-preparation|dispatch-to-court|mark-delivered}")
    public OrderResponse action(@PathVariable("id") Long id, @PathVariable("action") String action,
                                Principal principal) {
        return orders.staffAction(id, principal.getName(), action);
    }
}
