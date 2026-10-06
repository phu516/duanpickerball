package com.pickleball.order.controller;

import com.pickleball.cart.controller.GuestCartController;
import com.pickleball.order.dto.OrderResponse;
import com.pickleball.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/guest")
public class GuestOrderController {
    private final OrderService orders;

    public GuestOrderController(OrderService orders) { this.orders = orders; }

    @PostMapping("/order-quotes")
    public OrderService.Quote quote(@CookieValue(GuestCartController.COOKIE_NAME) String cookie,
                                    @RequestBody QuoteRequest request) {
        return orders.quote(cookie, request.fulfillmentMethod());
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@CookieValue(GuestCartController.COOKIE_NAME) String cookie,
                                @Valid @RequestBody OrderService.CreateOrder request,
                                @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        return orders.create(cookie, request, requestKey);
    }

    @GetMapping("/orders/{code}")
    public OrderResponse detail(@PathVariable("code") String code,
                                @RequestHeader("X-Guest-Access-Token") String token) {
        return orders.guestDetail(code, token);
    }

    @PostMapping("/orders/{code}/cancel")
    public OrderResponse cancel(@PathVariable("code") String code,
                                @RequestHeader("X-Guest-Access-Token") String token) {
        return orders.guestCancel(code, token);
    }

    public record QuoteRequest(String fulfillmentMethod) {}
}
