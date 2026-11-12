package com.localcart.controller;

import com.localcart.dto.AddToCartRequest;
import com.localcart.dto.CheckoutRequest;
import com.localcart.dto.CreateShopRequest;
import com.localcart.dto.InitiatePaymentRequest;
import com.localcart.dto.UpdateCartItemRequest;
import com.localcart.dto.VerifyPaymentRequest;
import com.localcart.entity.DeliveryMode;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import com.localcart.service.AnalyticsService;
import com.localcart.service.CartService;
import com.localcart.service.CustomerMarketplaceService;
import com.localcart.service.NotificationService;
import com.localcart.service.OrderService;
import com.localcart.service.PaymentService;
import com.localcart.service.ShopService;
import com.localcart.service.ShopkeeperAiInsightsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerAndGeneralControllersCoverageTest {

    private MockedStatic<SecurityUtils> securityUtils;
    private UserRepository userRepository;
    private OrderService orderService;
    private ShopService shopService;
    private CartService cartService;
    private CustomerMarketplaceService marketplaceService;
    private NotificationService notificationService;
    private PaymentService paymentService;
    private ShopkeeperAiInsightsService insightsService;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        userRepository = mock(UserRepository.class);
        orderService = mock(OrderService.class);
        shopService = mock(ShopService.class);
        cartService = mock(CartService.class);
        marketplaceService = mock(CustomerMarketplaceService.class);
        notificationService = mock(NotificationService.class);
        paymentService = mock(PaymentService.class);
        insightsService = mock(ShopkeeperAiInsightsService.class);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void shopkeeperControllerHandlesUnauthenticatedMissingAndWrongRoleCases() {
        ShopkeeperController controller = new ShopkeeperController(shopService, userRepository);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);
        assertEquals(401, controller.getMyShops().getStatusCode().value());
        assertEquals(401, controller.createShop(new CreateShopRequest()).getStatusCode().value());

        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertEquals(404, controller.getMyShops().getStatusCode().value());

        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(10L, Role.CUSTOMER, true)));
        assertEquals(403, controller.getMyShops().getStatusCode().value());
    }

    @Test
    void shopkeeperControllerReturnsOwnedShopsAndDelegatesCreation() {
        ShopkeeperController controller = new ShopkeeperController(shopService, userRepository);
        User owner = user(12L, Role.SHOPKEEPER, true);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));

        CreateShopRequest request = new CreateShopRequest();
        assertEquals(200, controller.getMyShops().getStatusCode().value());
        assertEquals(200, controller.createShop(request).getStatusCode().value());
        verify(shopService).getShopsByOwner(12L);
        verify(shopService).createShop("owner@example.com", request);
    }

    @Test
    void customerControllerEnforcesAuthenticationExistenceAndCustomerRole() {
        CustomerController controller = new CustomerController(orderService, userRepository);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);
        assertEquals(401, controller.getMyOrders().getStatusCode().value());

        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertEquals(404, controller.getMyOrders().getStatusCode().value());

        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(user(12L, Role.SHOPKEEPER, true)));
        assertEquals(403, controller.getMyOrders().getStatusCode().value());
    }

    @Test
    void customerControllerReturnsNotFoundOrOrdersForActiveCustomer() {
        CustomerController controller = new CustomerController(orderService, userRepository);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(10L, Role.CUSTOMER, true)));
        when(orderService.getOrdersByCustomer(10L)).thenReturn(Optional.empty());
        assertEquals(404, controller.getMyOrders().getStatusCode().value());
        when(orderService.getOrdersByCustomer(10L)).thenReturn(Optional.of(List.of()));
        assertEquals(200, controller.getMyOrders().getStatusCode().value());
    }

    @Test
    void cartControllerDelegatesCartMutationAndCheckoutEndpoints() {
        CartController controller = new CartController(cartService, orderService);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        AddToCartRequest add = new AddToCartRequest();
        UpdateCartItemRequest update = new UpdateCartItemRequest();
        CheckoutRequest checkout = new CheckoutRequest();
        checkout.setDeliveryMode(DeliveryMode.PLATFORM_DELIVERY);
        checkout.setAddressId(25L);

        assertEquals(200, controller.getCart(1L).getStatusCode().value());
        assertEquals(200, controller.addToCart(1L, add).getStatusCode().value());
        assertEquals(200, controller.updateCartItem(1L, 99L, update).getStatusCode().value());
        assertEquals(200, controller.removeFromCart(1L, 99L).getStatusCode().value());
        assertEquals(200, controller.checkout(1L, checkout).getStatusCode().value());

        verify(cartService).getCart("customer@example.com", 1L);
        verify(cartService).addToCart("customer@example.com", 1L, add);
        verify(cartService).updateCartItem("customer@example.com", 1L, 99L, update);
        verify(cartService).removeFromCart("customer@example.com", 1L, 99L);
        verify(orderService).checkoutCart("customer@example.com", 1L, DeliveryMode.PLATFORM_DELIVERY, 25L);
    }

    @Test
    void marketplaceControllerDelegatesAllReadEndpoints() {
        CustomerMarketplaceController controller = new CustomerMarketplaceController(marketplaceService);

        assertEquals(200, controller.getActiveShops().getStatusCode().value());
        assertEquals(200, controller.getActiveShop(1L).getStatusCode().value());
        assertEquals(200, controller.getShopProducts(1L).getStatusCode().value());
        assertEquals(200, controller.searchProducts("rice", "Staples", 1L,
                10.0, 100.0, 0, 20, "price", "desc").getStatusCode().value());
        assertEquals(200, controller.compareProductPrices(99L).getStatusCode().value());
        assertEquals(200, controller.getAvailableCategories().getStatusCode().value());

        verify(marketplaceService).getActiveShops();
        verify(marketplaceService).getActiveShop(1L);
        verify(marketplaceService).getShopProducts(1L);
        verify(marketplaceService).searchProducts("rice", "Staples", 1L,
                10.0, 100.0, 0, 20, "price", "desc");
        verify(marketplaceService).compareProductPrices(99L);
        verify(marketplaceService).getAvailableCategories();
    }

    @Test
    void notificationControllerDelegatesReadMarkAndDeleteActions() {
        NotificationController controller = new NotificationController(notificationService);
        when(notificationService.getMyNotifications(false, 0, 20)).thenReturn(Page.empty());

        assertEquals(200, controller.getMyNotifications(false, 0, 20).getStatusCode().value());
        when(notificationService.getUnreadCount()).thenReturn(3L);
        assertEquals(3L, controller.getUnreadCount().getBody().get("unreadCount"));
        assertEquals(200, controller.markAsRead(8L).getStatusCode().value());
        when(notificationService.markAllAsRead()).thenReturn(2);
        assertEquals(2, controller.markAllAsRead().getBody().get("updatedCount"));
        assertEquals(204, controller.deleteNotification(8L).getStatusCode().value());

        verify(notificationService).getMyNotifications(false, 0, 20);
        verify(notificationService).getUnreadCount();
        verify(notificationService).markAsRead(8L);
        verify(notificationService).markAllAsRead();
        verify(notificationService).deleteNotification(8L);
    }

    @Test
    void paymentControllerDelegatesInitiationVerificationLookupCollectionAndRefunds() {
        PaymentController controller = new PaymentController(paymentService);
        InitiatePaymentRequest initiate = new InitiatePaymentRequest();
        VerifyPaymentRequest verifyRequest = new VerifyPaymentRequest();

        assertEquals(200, controller.initiatePayment(50L, initiate).getStatusCode().value());
        assertEquals(200, controller.verifyPayment(verifyRequest).getStatusCode().value());
        assertEquals(200, controller.getPaymentForOrder(50L).getStatusCode().value());
        assertEquals(200, controller.collectCashOnDelivery(60L).getStatusCode().value());
        assertEquals(200, controller.refundPayment(60L).getStatusCode().value());
        assertEquals(200, controller.confirmCodRefund(60L).getStatusCode().value());

        verify(paymentService).initiatePayment(50L, initiate);
        verify(paymentService).verifyPayment(verifyRequest);
        verify(paymentService).getPaymentForOrder(50L);
        verify(paymentService).collectCashOnDelivery(60L);
        verify(paymentService).refundPaymentByAdmin(60L);
        verify(paymentService).confirmCodRefundByAdmin(60L);
    }

    @Test
    void shopkeeperAiInsightsControllerDelegatesForecastReplenishmentAndSummary() {
        ShopkeeperAiInsightsController controller = new ShopkeeperAiInsightsController(insightsService);

        assertEquals(200, controller.getProductForecast(1L, 10L, 7, 28)
                .getStatusCode().value());
        assertEquals(200, controller.getReplenishmentRecommendations(1L, 28, 3)
                .getStatusCode().value());
        assertEquals(200, controller.getSummary(1L, 28).getStatusCode().value());

        verify(insightsService).getProductForecast(1L, 10L, 7, 28);
        verify(insightsService).getReplenishmentRecommendations(1L, 28, 3);
        verify(insightsService).getSummary(1L, 28);
    }

    @Test
    void profileControllerReturnsUnauthorizedNotFoundAndUsesASafeUserResponse() {
        ProfileController controller = new ProfileController(userRepository);
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(null);
        assertEquals(401, controller.getProfile().getStatusCode().value());

        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertEquals(404, controller.getProfile().getStatusCode().value());

        User user = new User();
        user.setName("Test User");
        user.setEmail("customer@example.com");
        user.setPassword("not-returned");
        securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn("customer@example.com");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        var response = controller.getProfile();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Test User", response.getBody().getName());
        assertEquals("customer@example.com", response.getBody().getEmail());
        assertEquals("not-returned", user.getPassword());
    }

    private static User user(Long id, Role role, boolean active) {
        User user = new User();
        user.setName("Test " + role.name());
        user.setEmail(role == Role.SHOPKEEPER
                ? "owner@example.com" : "customer@example.com");
        user.setRole(role);
        user.setActive(active);
        setEntityId(user, id);
        return user;
    }

    private static void setEntityId(Object entity, Long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to assign test entity ID", exception);
        }
    }
}
