package com.pickleball.inventory;

import com.pickleball.cart.service.CartService;
import com.pickleball.category.entity.Category;
import com.pickleball.category.repository.CategoryRepository;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.order.service.OrderService;
import com.pickleball.product.entity.Product;
import com.pickleball.product.entity.ProductFulfillmentOption;
import com.pickleball.product.entity.ProductVariant;
import com.pickleball.product.repository.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderStockIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductFulfillmentRepository fulfillment;
    @Autowired CartService carts;
    @Autowired OrderService orders;

    @Test
    void onlyOneCustomerCanReserveLastSku() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categories.save(new Category("Danh mục " + suffix, "test-" + suffix, 1));
        Product product = products.save(new Product(category, "Vợt thử", "PickleZone", "Kiểm thử kho", "🏓"));
        ProductVariant variant = variants.save(new ProductVariant(product, "TEST-" + suffix,
                "Mặc định", BigDecimal.valueOf(100_000), 1));
        fulfillment.save(new ProductFulfillmentOption(product, "STORE_PICKUP"));
        String firstCart = carts.createOrRestore(null).token();
        String secondCart = carts.createOrRestore(null).token();
        carts.add(firstCart, variant.getId(), 1);
        carts.add(secondCart, variant.getId(), 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Boolean> a = pool.submit(() -> place(firstCart, ready, go));
            Future<Boolean> b = pool.submit(() -> place(secondCart, ready, go));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            ProductVariant after = variants.findById(variant.getId()).orElseThrow();
            assertEquals(1, after.getStockReserved());
            assertEquals(0, after.available());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void retryWithSameRequestKeyDoesNotCreateSecondOrder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categories.save(new Category("Danh mục " + suffix, "test-" + suffix, 1));
        Product product = products.save(new Product(category, "Vợt thử", "PickleZone", "Kiểm thử", "🏓"));
        ProductVariant variant = variants.save(new ProductVariant(product, "TEST-" + suffix,
                "Mặc định", BigDecimal.valueOf(100_000), 3));
        fulfillment.save(new ProductFulfillmentOption(product, "STORE_PICKUP"));
        String cart = carts.createOrRestore(null).token();
        carts.add(cart, variant.getId(), 1);
        String key = UUID.randomUUID().toString();
        var request = new OrderService.CreateOrder("Khách thử", "0901234567", null,
                "STORE_PICKUP", null, null, null);
        var first = orders.create(cart, request, key);
        var retry = orders.create(cart, request, key);
        assertEquals(first.id(), retry.id());
        assertEquals(1, variants.findById(variant.getId()).orElseThrow().getStockReserved());
    }

    private boolean place(String cart, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        go.await();
        try {
            orders.create(cart, new OrderService.CreateOrder("Khách thử", "0901234567", null,
                    "STORE_PICKUP", null, null, null));
            return true;
        } catch (BusinessException exception) {
            assertEquals("INSUFFICIENT_STOCK", exception.code());
            return false;
        }
    }
}
