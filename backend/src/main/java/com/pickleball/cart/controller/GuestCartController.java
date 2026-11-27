package com.pickleball.cart.controller;

import com.pickleball.cart.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/guest/cart")
public class GuestCartController {
    public static final String COOKIE_NAME = "PZ_CART";
    private final CartService carts;
    private final String cookieSameSite;
    private final boolean secureCookies;

    public GuestCartController(CartService carts,
                               @Value("${server.servlet.session.cookie.same-site:lax}") String cookieSameSite,
                               @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        this.carts = carts;
        this.cookieSameSite = cookieSameSite;
        this.secureCookies = secureCookies;
    }

    @PostMapping
    public ResponseEntity<List<CartService.CartLine>> create(
            @CookieValue(value = COOKIE_NAME, required = false) String cookie,
            HttpServletRequest request) {
        CartService.CreatedCart result = carts.createOrRestore(cookie);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.newCookie()) {
            ResponseCookie newCookie = ResponseCookie.from(COOKIE_NAME, result.token())
                    .httpOnly(true).secure(secureCookies || request.isSecure()).sameSite(cookieSameSite)
                    .path("/api/v1/guest").maxAge(60L * 60 * 24 * 30).build();
            response.header(HttpHeaders.SET_COOKIE, newCookie.toString());
        }
        return response.body(carts.list(result.token()));
    }

    @GetMapping
    public List<CartService.CartLine> list(@CookieValue(COOKIE_NAME) String cookie) {
        return carts.list(cookie);
    }

    @PostMapping("/items")
    public List<CartService.CartLine> add(@CookieValue(COOKIE_NAME) String cookie,
                                          @RequestBody QuantityRequest request) {
        return carts.add(cookie, request.variantId(), request.quantity());
    }

    @PatchMapping("/items/{id}")
    public List<CartService.CartLine> change(@CookieValue(COOKIE_NAME) String cookie,
                                             @PathVariable("id") Long id,
                                             @RequestBody QuantityRequest request) {
        return carts.change(cookie, id, request.quantity());
    }

    @DeleteMapping("/items/{id}")
    public List<CartService.CartLine> remove(@CookieValue(COOKIE_NAME) String cookie,
                                             @PathVariable("id") Long id) {
        return carts.remove(cookie, id);
    }

    @DeleteMapping("/items")
    public void clear(@CookieValue(COOKIE_NAME) String cookie) { carts.clear(cookie); }

    public record QuantityRequest(Long variantId, int quantity) {}
}
