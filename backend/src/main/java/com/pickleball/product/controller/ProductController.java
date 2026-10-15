package com.pickleball.product.controller;

import com.pickleball.product.dto.ProductResponse;
import com.pickleball.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) { this.products = products; }

    @GetMapping("/products")
    public List<ProductResponse> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Long categoryId) {
        return products.list(keyword, categoryId, false);
    }

    @GetMapping("/products/{id}")
    public ProductResponse detail(@PathVariable Long id) { return products.get(id); }

    @GetMapping("/admin/products")
    public List<ProductResponse> adminList(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long categoryId) {
        return products.list(keyword, categoryId, true);
    }

    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductService.ProductRequest request) {
        return products.create(request);
    }

    @PatchMapping("/admin/products/{id}")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductService.ProductUpdate request) {
        return products.update(id, request);
    }

    @PutMapping("/admin/products/{id}/fulfillment-options")
    public ProductResponse fulfillment(@PathVariable Long id, @RequestBody List<String> methods) {
        return products.updateFulfillment(id, methods);
    }

    @PostMapping("/admin/products/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse addVariant(@PathVariable Long id,
            @Valid @RequestBody ProductService.VariantRequest request) {
        return products.addVariant(id, request);
    }

    @PatchMapping("/admin/product-variants/{id}")
    public ProductResponse updateVariant(@PathVariable Long id,
            @Valid @RequestBody ProductService.VariantUpdate request) {
        return products.updateVariant(id, request);
    }

    @PutMapping("/admin/product-variants/{id}/image")
    public ProductResponse variantImage(@PathVariable Long id, @RequestBody VariantImageRequest request) {
        return products.updateVariantImage(id, request.imageUrl());
    }

    public record VariantImageRequest(String imageUrl) {}

    @PutMapping("/admin/products/{id}/image")
    public ProductResponse image(@PathVariable Long id, @RequestBody VariantImageRequest request) {
        return products.updateImage(id, request.imageUrl());
    }
}
