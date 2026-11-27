package com.pickleball.product.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id") private Product product;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id") private ProductVariant variant;
    @Column(nullable = false, length = 500) private String url;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean primaryImage;

    protected ProductImage() {}

    public ProductImage(Product product, ProductVariant variant, String url) {
        this.product = product;
        this.variant = variant;
        this.url = url;
        this.sortOrder = 0;
        this.primaryImage = true;
    }

    public ProductVariant getVariant() { return variant; }
    public Product getProduct() { return product; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
