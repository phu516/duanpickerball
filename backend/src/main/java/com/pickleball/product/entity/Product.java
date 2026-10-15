package com.pickleball.product.entity;

import com.pickleball.category.entity.Category;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id") private Category category;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String brand;
    private String description;
    @Column(length = 500) private String imageUrl;
    @Column(nullable = false) private String emoji;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected Product() {}

    public Product(Category category, String name, String brand, String description, String emoji) {
        this.category = category;
        this.name = name;
        this.brand = brand;
        this.description = description;
        this.emoji = emoji;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getEmoji() { return emoji; }
    public boolean isActive() { return active; }

    public void update(Category category, String name, String brand, String description,
                       String emoji, boolean active) {
        this.category = category;
        this.name = name;
        this.brand = brand;
        this.description = description;
        this.emoji = emoji;
        this.active = active;
    }
}
