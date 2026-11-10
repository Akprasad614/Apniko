package com.localcart.service;

import com.localcart.dto.AddToCartRequest;
import com.localcart.dto.CartItemResponse;
import com.localcart.dto.CartResponse;
import com.localcart.dto.UpdateCartItemRequest;
import com.localcart.entity.Cart;
import com.localcart.entity.CartItem;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.CartItemRepository;
import com.localcart.repository.CartRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ShopInventoryRepository inventoryRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            ShopRepository shopRepository,
            ShopInventoryRepository inventoryRepository) {

        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public CartResponse getCart(
            String email,
            Long shopId) {

        User customer = getCustomer(email);

        Shop shop = getActiveShop(shopId);

        Cart cart = cartRepository
                .findByCustomerIdAndShopId(
                        customer.getId(),
                        shopId
                )
                .orElse(null);

        if (cart == null) {
            return emptyCart(shop);
        }

        return toResponse(cart);
    }

    @Transactional
    public CartResponse addToCart(
            String email,
            Long shopId,
            AddToCartRequest request) {

        User customer = getCustomer(email);

        Shop shop = getActiveShop(shopId);

        ShopInventory inventory =
                inventoryRepository
                        .findByShopIdAndProductId(
                                shopId,
                                request.getProductId()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product is not available in this shop"
                                ));

        if (!inventory.isAvailable()
                || inventory.getStockQuantity() <= 0) {

            throw new BadRequestException(
                    "Product is currently unavailable"
            );
        }

        Cart cart = cartRepository
                .findByCustomerIdAndShopId(
                        customer.getId(),
                        shopId
                )
                .orElseGet(() -> {

                    Cart newCart = new Cart();
                    newCart.setCustomer(customer);
                    newCart.setShop(shop);

                    return cartRepository.save(newCart);
                });

        CartItem cartItem =
                cartItemRepository
                        .findByCartIdAndProductId(
                                cart.getId(),
                                request.getProductId()
                        )
                        .orElse(null);

        int newQuantity = request.getQuantity();

        if (cartItem != null) {

            newQuantity += cartItem.getQuantity();

        } else {

            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(inventory.getProduct());
        }

        if (newQuantity > inventory.getStockQuantity()) {

            throw new BadRequestException(
                    "Requested quantity exceeds available stock"
            );
        }

        cartItem.setQuantity(newQuantity);

        cartItemRepository.save(cartItem);

        // Keep the inverse side of the relationship in sync so the immediate
        // POST response includes the item just saved.
        if (!cart.getItems().contains(cartItem)) {
            cart.getItems().add(cartItem);
        }

        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateCartItem(
            String email,
            Long shopId,
            Long productId,
            UpdateCartItemRequest request) {

        User customer = getCustomer(email);

        Cart cart = getCartEntity(
                customer.getId(),
                shopId
        );

        ShopInventory inventory =
                inventoryRepository
                        .findByShopIdAndProductId(
                                shopId,
                                productId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product is not available in this shop"
                                ));

        if (!inventory.isAvailable()) {
            throw new BadRequestException(
                    "Product is currently unavailable"
            );
        }

        if (request.getQuantity()
                > inventory.getStockQuantity()) {

            throw new BadRequestException(
                    "Requested quantity exceeds available stock"
            );
        }

        CartItem cartItem =
                cartItemRepository
                        .findByCartIdAndProductId(
                                cart.getId(),
                                productId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product is not in the cart"
                                ));

        cartItem.setQuantity(request.getQuantity());

        cartItemRepository.save(cartItem);

        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeFromCart(
            String email,
            Long shopId,
            Long productId) {

        User customer = getCustomer(email);

        Cart cart = getCartEntity(
                customer.getId(),
                shopId
        );

        CartItem cartItem =
                cartItemRepository
                        .findByCartIdAndProductId(
                                cart.getId(),
                                productId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product is not in the cart"
                                ));

        cart.getItems().remove(cartItem);

        cartItemRepository.delete(cartItem);

        return toResponse(cart);
    }

    private User getCustomer(String email) {

        if (email == null) {
            throw new BadRequestException(
                    "User is not authenticated"
            );
        }

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        ));

        if (user.getRole() != Role.CUSTOMER) {
            throw new BadRequestException(
                    "Only a customer can use the cart"
            );
        }

        if (!user.isActive()) {
            throw new BadRequestException(
                    "Customer account is inactive"
            );
        }

        return user;
    }

    private Shop getActiveShop(Long shopId) {

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Shop not found"
                        ));

        if (!shop.isActive()) {
            throw new BadRequestException(
                    "Shop is not active"
            );
        }

        return shop;
    }

    private Cart getCartEntity(
            Long customerId,
            Long shopId) {

        return cartRepository
                .findByCustomerIdAndShopId(
                        customerId,
                        shopId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Cart not found"
                        ));
    }

    private CartResponse emptyCart(Shop shop) {

        CartResponse response = new CartResponse();

        response.setShopId(shop.getId());
        response.setShopName(shop.getName());
        response.setTotalAmount(0);

        return response;
    }

    private CartResponse toResponse(Cart cart) {

        CartResponse response = new CartResponse();

        response.setCartId(cart.getId());
        response.setShopId(cart.getShop().getId());
        response.setShopName(cart.getShop().getName());

        double total = 0;

        for (CartItem item : cart.getItems()) {

            ShopInventory inventory =
                    inventoryRepository
                            .findByShopIdAndProductId(
                                    cart.getShop().getId(),
                                    item.getProduct().getId()
                            )
                            .orElse(null);

            if (inventory == null) {
                continue;
            }

            CartItemResponse itemResponse =
                    new CartItemResponse();

            itemResponse.setId(item.getId());
            itemResponse.setProductId(
                    item.getProduct().getId()
            );
            itemResponse.setProductName(
                    item.getProduct().getName()
            );
            itemResponse.setCategory(
                    item.getProduct().getCategory()
            );
            itemResponse.setPrice(
                    inventory.getPrice()
            );
            itemResponse.setQuantity(
                    item.getQuantity()
            );

            double lineTotal =
                    inventory.getPrice()
                            * item.getQuantity();

            itemResponse.setLineTotal(lineTotal);

            itemResponse.setAvailable(
                    inventory.isAvailable()
                            && inventory.getStockQuantity() > 0
            );

            itemResponse.setAvailableStock(
                    inventory.getStockQuantity()
            );

            response.getItems().add(itemResponse);

            total += lineTotal;
        }

        response.setTotalAmount(total);

        return response;
    }
}