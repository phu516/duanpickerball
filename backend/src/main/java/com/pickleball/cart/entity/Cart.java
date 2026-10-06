package com.pickleball.cart.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "carts")
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String accessHash;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected Cart() {}

    public Cart(String accessHash) {
        this.accessHash = accessHash;
        this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public void touch() { updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC); }
}
