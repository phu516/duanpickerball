package com.pickleball.product.service;

import com.pickleball.category.entity.Category;
import com.pickleball.category.repository.CategoryRepository;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.product.dto.ProductResponse;
import com.pickleball.product.entity.*;
import com.pickleball.product.repository.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductFulfillmentRepository fulfillment;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, ProductVariantRepository variants,
                          ProductFulfillmentRepository fulfillment, CategoryRepository categories) {
        this.products = products;
        this.variants = variants;
        this.fulfillment = fulfillment;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(String keyword, Long categoryId, boolean includeInactive) {
        return products.findAll().stream()
                .filter(product -> includeInactive || product.isActive())
                .filter(product -> categoryId == null || product.getCategory().getId().equals(categoryId))
                .filter(product -> keyword == null || product.getName().toLowerCase()
                        .contains(keyword.trim().toLowerCase()))
                .map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) { return map(requireProduct(id)); }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = requireCategory(request.categoryId());
        Product product = products.save(new Product(category, request.name(), request.brand(),
                request.description(), request.emoji()));
        variants.save(new ProductVariant(product, request.sku(), "Mặc định",
                request.price(), request.stock()));
        for (String method : request.fulfillmentOptions()) {
            fulfillment.save(new ProductFulfillmentOption(product, method));
        }
        return map(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdate request) {
        Product product = requireProduct(id);
        product.update(requireCategory(request.categoryId()), request.name(), request.brand(),
                request.description(), request.emoji(), request.active());
        return map(product);
    }

    @Transactional
    public ProductResponse updateFulfillment(Long id, List<String> methods) {
        Product product = requireProduct(id);
        fulfillment.deleteByProductId(id);
        for (String method : methods) fulfillment.save(new ProductFulfillmentOption(product, method));
        return map(product);
    }

    @Transactional
    public ProductResponse addVariant(Long productId, VariantRequest request) {
        Product product = requireProduct(productId);
        variants.save(new ProductVariant(product, request.sku(), request.label(),
                request.price(), request.stock()));
        return map(product);
    }

    @Transactional
    public ProductResponse updateVariant(Long id, VariantUpdate request) {
        ProductVariant variant = variants.findById(id).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Không tìm thấy SKU"));
        variant.update(request.label(), request.price(), request.active());
        return map(variant.getProduct());
    }

    private ProductResponse map(Product product) {
        List<ProductVariant> allVariants = variants.findByProductIdOrderByIdAsc(product.getId());
        ProductVariant variant = allVariants.stream()
                .findFirst().orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,
                        "NO_VARIANT", "Sản phẩm chưa có SKU"));
        return new ProductResponse(product.getId(), variant.getId(), product.getName(),
                product.getBrand(), product.getCategory().getName(), product.getDescription(),
                product.getImageUrl(), product.getEmoji(), variant.getPrice(), variant.available(),
                variant.getSku(), fulfillment.findByProductId(product.getId()).stream()
                .map(ProductFulfillmentOption::getMethod).toList(),
                allVariants.stream().map(v -> new ProductResponse.Variant(v.getId(),
                        v.getSku(), v.getLabel(), v.getPrice(), v.available(), v.isActive())).toList());
    }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
    }

    private Category requireCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "CATEGORY_NOT_FOUND", "Không tìm thấy danh mục"));
    }

    public record ProductRequest(@NotNull Long categoryId, @NotBlank String name,
                                 @NotBlank String brand, String description, @NotBlank String emoji,
                                 @NotBlank String sku, @NotNull @DecimalMin("0.01") BigDecimal price,
                                 @Min(0) int stock, @NotEmpty List<String> fulfillmentOptions) {}

    public record ProductUpdate(@NotNull Long categoryId, @NotBlank String name,
                                @NotBlank String brand, String description,
                                @NotBlank String emoji, boolean active) {}
    public record VariantRequest(@NotBlank String sku, @NotBlank String label,
                                 @NotNull @DecimalMin("0.01") BigDecimal price, @Min(0) int stock) {}
    public record VariantUpdate(@NotBlank String label,
                                @NotNull @DecimalMin("0.01") BigDecimal price, boolean active) {}
}
