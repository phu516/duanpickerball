package com.pickleball.product.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_fulfillment_options")
public class ProductFulfillmentOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id") private Product product;
    @Column(nullable = false) private String method;

    protected ProductFulfillmentOption() {}

    public ProductFulfillmentOption(Product product, String method) {
        this.product = product;
        this.method = method;
    }

    public Long getId() { return id; }
    public String getMethod() { return method; }
}
