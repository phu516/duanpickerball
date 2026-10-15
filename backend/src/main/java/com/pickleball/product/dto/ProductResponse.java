package com.pickleball.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(Long id, Long variantId, String name, String brand,
                              String category, String description, String imageUrl,
                              String emoji, BigDecimal price, int stock,
                              String sku, List<String> fulfillmentOptions,
                              List<Variant> variants) {
    public record Variant(Long id, String sku, String label, BigDecimal price,
                          int stock, boolean active, String imageUrl) {}
}
