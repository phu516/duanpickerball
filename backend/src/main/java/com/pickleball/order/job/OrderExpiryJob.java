package com.pickleball.order.job;

import com.pickleball.order.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpiryJob {
    private final OrderService orders;

    public OrderExpiryJob(OrderService orders) { this.orders = orders; }

    @Scheduled(fixedDelay = 60_000)
    public void expireUnconfirmedOrders() { orders.expireNewOrders(); }
}
