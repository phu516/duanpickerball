package com.pickleball.order.service;

import com.pickleball.auth.entity.UserAccount;
import com.pickleball.auth.repository.UserAccountRepository;
import com.pickleball.booking.entity.Booking;
import com.pickleball.booking.service.BookingService;
import com.pickleball.cart.entity.Cart;
import com.pickleball.cart.entity.CartItem;
import com.pickleball.cart.service.CartService;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.common.idempotency.IdempotencyService;
import com.pickleball.common.security.GuestAccessService;
import com.pickleball.inventory.service.InventoryService;
import com.pickleball.order.dto.OrderResponse;
import com.pickleball.order.entity.*;
import com.pickleball.order.repository.*;
import com.pickleball.product.entity.ProductVariant;
import com.pickleball.product.repository.ProductFulfillmentRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CartService carts;
    private final InventoryService inventory;
    private final ProductFulfillmentRepository fulfillment;
    private final BookingService bookings;
    private final GuestAccessService guestAccess;
    private final UserAccountRepository users;
    private final IdempotencyService idempotency;

    public OrderService(OrderRepository orders, OrderItemRepository orderItems, CartService carts,
                        InventoryService inventory, ProductFulfillmentRepository fulfillment,
                        BookingService bookings, GuestAccessService guestAccess,
                        UserAccountRepository users, IdempotencyService idempotency) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.carts = carts;
        this.inventory = inventory;
        this.fulfillment = fulfillment;
        this.bookings = bookings;
        this.guestAccess = guestAccess;
        this.users = users;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Quote quote(String cartCookie, String method) {
        Cart cart = carts.requireCart(cartCookie);
        List<CartItem> items = carts.entities(cart);
        checkItems(items, method);
        BigDecimal subtotal = items.stream().map(item -> item.getVariant().getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Quote(subtotal, BigDecimal.ZERO, subtotal, "VND");
    }

    @Transactional
    public OrderResponse create(String cartCookie, CreateOrder request) {
        return create(cartCookie, request, null);
    }

    @Transactional
    public OrderResponse create(String cartCookie, CreateOrder request, String requestKey) {
        String scope = "ORDER:" + idempotency.hash(cartCookie);
        var previous = idempotency.previous(scope, requestKey, request);
        if (previous.isPresent()) {
            StoreOrder old = byId(previous.get());
            return map(old, guestAccess.issue("ORDER", old.getId()));
        }
        Cart cart = carts.requireCart(cartCookie);
        List<CartItem> cartItems = carts.entities(cart);
        if (cartItems.isEmpty()) throw bad("EMPTY_CART", "Giỏ hàng đang trống");
        checkItems(cartItems, request.fulfillmentMethod());
        Booking booking = validateDelivery(request);

        // Lock SKU in stable order so two customers cannot reserve the last item simultaneously.
        Map<Long, ProductVariant> locked = new HashMap<>();
        cartItems.stream().map(item -> item.getVariant().getId()).distinct().sorted()
                .forEach(id -> locked.put(id, inventory.lockVariant(id)));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            ProductVariant variant = locked.get(item.getVariant().getId());
            if (item.getQuantity() > variant.available()) {
                throw conflict("INSUFFICIENT_STOCK", "Không đủ tồn kho cho " + variant.getSku());
            }
            subtotal = subtotal.add(variant.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        String code = "DH" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        StoreOrder order = orders.save(new StoreOrder(code, booking, request.name().trim(),
                request.phone().trim(), request.email(), request.fulfillmentMethod(),
                request.deliveryAddress(), subtotal, BigDecimal.ZERO));
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = locked.get(cartItem.getVariant().getId());
            OrderItem item = orderItems.save(new OrderItem(order, variant, cartItem.getQuantity()));
            inventory.reserve(item, variant, cartItem.getQuantity());
        }
        idempotency.remember(scope, requestKey, request, "ORDER", order.getId());
        carts.clear(cartCookie);
        return map(order, guestAccess.issue("ORDER", order.getId()));
    }

    @Transactional(readOnly = true)
    public OrderResponse guestDetail(String code, String token) {
        StoreOrder order = byCode(code);
        guestAccess.require(token, "ORDER", order.getId());
        return map(order, null);
    }

    @Transactional
    public OrderResponse guestCancel(String code, String token) {
        StoreOrder order = byCode(code);
        guestAccess.require(token, "ORDER", order.getId());
        cancel(order);
        return map(order, null);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> adminList() {
        return orders.findAll().stream().map(order -> map(order, null)).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse adminDetail(Long id) { return map(byId(id), null); }

    @Transactional
    public OrderResponse adminAction(Long id, String action) {
        StoreOrder order = byId(id);
        String current = order.getStatus();
        String next = switch (action) {
            case "confirm" -> current.equals("NEW") ? "CONFIRMED" : null;
            case "prepare" -> current.equals("CONFIRMED") ? "PREPARING" : null;
            case "ready-for-pickup" -> current.equals("PREPARING")
                    && order.getFulfillmentMethod().equals("STORE_PICKUP") ? "READY_FOR_PICKUP" : null;
            case "ship" -> current.equals("PREPARING")
                    && order.getFulfillmentMethod().equals("HOME_DELIVERY") ? "SHIPPING" : null;
            case "complete" -> List.of("READY_FOR_PICKUP", "SHIPPING").contains(current)
                    ? "COMPLETED" : null;
            case "cancel" -> List.of("NEW", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP")
                    .contains(current) ? "CANCELLED" : null;
            default -> null;
        };
        if (next == null) throw conflict("INVALID_ORDER_TRANSITION", "Không thể đổi trạng thái đơn theo thao tác này");
        if (next.equals("CANCELLED")) inventory.release(orderItems.findByOrderId(id));
        if (next.equals("COMPLETED")) inventory.fulfill(orderItems.findByOrderId(id));
        order.changeStatus(next);
        return map(order, null);
    }

    @Transactional
    public OrderResponse assign(Long id, String username) {
        StoreOrder order = byId(id);
        if (!order.getFulfillmentMethod().equals("COURT_DELIVERY")) {
            throw conflict("NOT_COURT_DELIVERY", "Đơn này không giao đến sân");
        }
        UserAccount staff = users.findByUsername(username).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND", "Không tìm thấy nhân viên"));
        if (!staff.getRole().equals("STAFF")) throw bad("INVALID_STAFF", "Tài khoản không có vai trò nhân viên");
        order.assign(staff);
        return map(order, null);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> staffOrders(String username) {
        return orders.findByAssignedStaffUsernameAndFulfillmentMethod(username, "COURT_DELIVERY")
                .stream().map(order -> map(order, null)).toList();
    }

    @Transactional
    public OrderResponse staffAction(Long id, String username, String action) {
        StoreOrder order = byId(id);
        if (order.getAssignedStaff() == null
                || !order.getAssignedStaff().getUsername().equals(username)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED", "Đơn không được giao cho bạn");
        }
        String current = order.getStatus();
        String next = switch (action) {
            case "start-preparation" -> current.equals("CONFIRMED") ? "PREPARING" : null;
            case "dispatch-to-court" -> current.equals("PREPARING") ? "OUT_FOR_COURT_DELIVERY" : null;
            case "mark-delivered" -> current.equals("OUT_FOR_COURT_DELIVERY") ? "DELIVERED" : null;
            default -> null;
        };
        if (next == null) throw conflict("INVALID_ORDER_TRANSITION", "Không thể đổi trạng thái đơn");
        if (next.equals("DELIVERED")) inventory.fulfill(orderItems.findByOrderId(id));
        order.changeStatus(next);
        return map(order, null);
    }

    @Transactional
    public int expireNewOrders() {
        List<StoreOrder> stale = orders.findByStatusAndCreatedAtBefore("NEW",
                LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        stale.forEach(this::cancel);
        return stale.size();
    }

    public StoreOrder byCode(String code) {
        return orders.findByOrderCode(code).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
    }

    public StoreOrder byId(Long id) {
        return orders.findById(id).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
    }

    private Booking validateDelivery(CreateOrder request) {
        return switch (request.fulfillmentMethod()) {
            case "STORE_PICKUP" -> null;
            case "HOME_DELIVERY" -> {
                if (request.deliveryAddress() == null || request.deliveryAddress().isBlank()) {
                    throw bad("ADDRESS_REQUIRED", "Cần địa chỉ giao hàng");
                }
                yield null;
            }
            case "COURT_DELIVERY" -> {
                if (request.bookingCode() == null || request.bookingToken() == null) {
                    throw bad("BOOKING_REQUIRED", "Cần phiếu và quyền truy cập phiếu sân");
                }
                Booking booking = bookings.byCode(request.bookingCode());
                guestAccess.require(request.bookingToken(), "BOOKING", booking.getId());
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                if (!booking.getStatus().equals("CHECKED_IN")
                        || now.isBefore(booking.getStartAt()) || !now.isBefore(booking.getEndAt())) {
                    throw conflict("BOOKING_NOT_PLAYING", "Phiếu sân chưa trong giờ chơi hoặc chưa nhận sân");
                }
                yield booking;
            }
            default -> throw bad("INVALID_FULFILLMENT", "Cách nhận hàng không hợp lệ");
        };
    }

    private void checkItems(List<CartItem> items, String method) {
        if (items.isEmpty()) throw bad("EMPTY_CART", "Giỏ hàng đang trống");
        for (CartItem item : items) {
            if (!fulfillment.existsByProductIdAndMethod(item.getVariant().getProduct().getId(), method)) {
                throw bad("FULFILLMENT_UNSUPPORTED", "Một sản phẩm không hỗ trợ cách nhận đã chọn");
            }
        }
    }

    private void cancel(StoreOrder order) {
        if (!List.of("NEW", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP")
                .contains(order.getStatus())) {
            throw conflict("ORDER_NOT_CANCELLABLE", "Đơn đã bàn giao hoặc đang giao không thể hủy");
        }
        inventory.release(orderItems.findByOrderId(order.getId()));
        order.changeStatus("CANCELLED");
    }

    private OrderResponse map(StoreOrder order, String token) {
        return OrderResponse.from(order, orderItems.findByOrderId(order.getId()), token);
    }

    private BusinessException bad(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }
    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public record CreateOrder(@NotBlank String name,
                              @NotBlank @Pattern(regexp = "[0-9+() .-]{9,16}") String phone,
                              @Email String email,
                              @NotBlank String fulfillmentMethod,
                              String deliveryAddress, String bookingCode,
                              String bookingToken) {}
    public record Quote(BigDecimal subtotal, BigDecimal deliveryFee,
                        BigDecimal totalAmount, String currency) {}
}
