package com.pickleball.cart.service;

import com.pickleball.cart.entity.*;
import com.pickleball.cart.repository.*;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.product.entity.ProductVariant;
import com.pickleball.product.entity.ProductImage;
import com.pickleball.product.repository.ProductImageRepository;
import com.pickleball.product.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final SecureRandom random = new SecureRandom();

    public CartService(CartRepository carts, CartItemRepository items,
                       ProductVariantRepository variants, ProductImageRepository images) {
        this.carts = carts;
        this.items = items;
        this.variants = variants;
        this.images = images;
    }

    @Transactional
    public CreatedCart createOrRestore(String cookie) {
        if (cookie != null) {
            Optional<Cart> existing = carts.findByAccessHash(hash(cookie));
            if (existing.isPresent()) return new CreatedCart(existing.get(), cookie, false);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new CreatedCart(carts.save(new Cart(hash(token))), token, true);
    }

    @Transactional(readOnly = true)
    public List<CartLine> list(String cookie) {
        Cart cart = requireCart(cookie);
        return items.findByCartIdOrderByIdAsc(cart.getId()).stream().map(this::map).toList();
    }

    @Transactional
    public List<CartLine> add(String cookie, Long variantId, int quantity) {
        if (quantity < 1) throw badQuantity();
        Cart cart = requireCart(cookie);
        ProductVariant variant = variants.findById(variantId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Không tìm thấy SKU"));
        if (!variant.isActive() || !variant.getProduct().isActive())
            throw new BusinessException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Sản phẩm chưa mở bán");
        CartItem item = items.findByCartIdAndVariantId(cart.getId(), variantId)
                .orElseGet(() -> new CartItem(cart, variant, 0));
        if (item.getQuantity() + quantity > variant.available()) throw insufficient();
        item.setQuantity(item.getQuantity() + quantity);
        items.save(item);
        cart.touch();
        return list(cookie);
    }

    @Transactional
    public List<CartLine> change(String cookie, Long itemId, int quantity) {
        if (quantity < 1) throw badQuantity();
        Cart cart = requireCart(cookie);
        CartItem item = ownedItem(cart, itemId);
        if (quantity > item.getVariant().available()) throw insufficient();
        item.setQuantity(quantity);
        cart.touch();
        return list(cookie);
    }

    @Transactional
    public List<CartLine> remove(String cookie, Long itemId) {
        Cart cart = requireCart(cookie);
        items.delete(ownedItem(cart, itemId));
        cart.touch();
        return list(cookie);
    }

    @Transactional
    public void clear(String cookie) {
        Cart cart = requireCart(cookie);
        items.deleteByCartId(cart.getId());
        cart.touch();
    }

    public Cart requireCart(String cookie) {
        if (cookie == null || cookie.isBlank()) throw missingCart();
        return carts.findByAccessHash(hash(cookie)).orElseThrow(this::missingCart);
    }

    public List<CartItem> entities(Cart cart) { return items.findByCartIdOrderByIdAsc(cart.getId()); }

    private CartItem ownedItem(Cart cart, Long id) {
        return items.findByIdAndCartId(id, cart.getId()).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "Không tìm thấy dòng giỏ hàng"));
    }

    private CartLine map(CartItem item) {
        ProductVariant variant = item.getVariant();
        String imageUrl = images.findByProductIdOrderByPrimaryImageDescSortOrderAscIdAsc(
                variant.getProduct().getId()).stream()
                .filter(image -> image.getVariant() != null
                        && image.getVariant().getId().equals(variant.getId()))
                .map(ProductImage::getUrl).findFirst().orElse(variant.getProduct().getImageUrl());
        return new CartLine(item.getId(), variant.getId(), variant.getProduct().getName(),
                variant.getLabel(), variant.getSku(), imageUrl, variant.available(),
                variant.getPrice(), item.getQuantity(),
                variant.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    private String hash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private BusinessException missingCart() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "CART_ACCESS_DENIED", "Chưa có quyền truy cập giỏ hàng");
    }

    private BusinessException badQuantity() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Số lượng phải lớn hơn 0");
    }

    private BusinessException insufficient() {
        return new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Số lượng vượt tồn có thể bán");
    }

    public record CreatedCart(Cart cart, String token, boolean newCookie) {}
    public record CartLine(Long itemId, Long variantId, String productName,
                           String variantLabel, String sku, String imageUrl, int available,
                           BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
}
