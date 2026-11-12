package com.localcart.controller;

import com.localcart.dto.AddToCartRequest;
import com.localcart.dto.CartResponse;
import com.localcart.dto.CheckoutRequest;
import com.localcart.dto.UpdateCartItemRequest;
import com.localcart.entity.Order;
import com.localcart.security.SecurityUtils;
import com.localcart.service.CartService;
import com.localcart.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/cart")
public class CartController {

    private final CartService cartService;
    private final OrderService orderService;

    public CartController(
            CartService cartService,
            OrderService orderService) {

        this.cartService = cartService;
        this.orderService = orderService;
    }

    @GetMapping("/{shopId}")
    public ResponseEntity<CartResponse> getCart(
            @PathVariable Long shopId) {

        String email = SecurityUtils.getCurrentUserEmail();

        return ResponseEntity.ok(
                cartService.getCart(email, shopId)
        );
    }

    @PostMapping("/{shopId}/items")
    public ResponseEntity<CartResponse> addToCart(
            @PathVariable Long shopId,
            @Valid @RequestBody AddToCartRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();

        return ResponseEntity.ok(
                cartService.addToCart(
                        email,
                        shopId,
                        request
                )
        );
    }

    @PutMapping("/{shopId}/items/{productId}")
    public ResponseEntity<CartResponse> updateCartItem(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();

        return ResponseEntity.ok(
                cartService.updateCartItem(
                        email,
                        shopId,
                        productId,
                        request
                )
        );
    }

    @DeleteMapping("/{shopId}/items/{productId}")
    public ResponseEntity<CartResponse> removeFromCart(
            @PathVariable Long shopId,
            @PathVariable Long productId) {

        String email = SecurityUtils.getCurrentUserEmail();

        return ResponseEntity.ok(
                cartService.removeFromCart(
                        email,
                        shopId,
                        productId
                )
        );
    }

    @PostMapping("/{shopId}/checkout")
    public ResponseEntity<Order> checkout(
            @PathVariable Long shopId,
            @Valid @RequestBody CheckoutRequest request) {

        String email = SecurityUtils.getCurrentUserEmail();

        return ResponseEntity.ok(
                orderService.checkoutCart(
                        email,
                        shopId,
                        request.getDeliveryMode(),
                        request.getAddressId()
                )
        );
    }
}