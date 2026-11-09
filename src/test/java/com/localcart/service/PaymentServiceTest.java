package com.localcart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localcart.dto.InitiatePaymentRequest;
import com.localcart.dto.PaymentResponse;
import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Payment;
import com.localcart.entity.PaymentMethod;
import com.localcart.entity.PaymentStatus;
import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.PaymentRepository;
import com.localcart.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private RazorpayGatewayClient razorpayGatewayClient;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        razorpayGatewayClient = mock(RazorpayGatewayClient.class);
        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                userRepository,
                razorpayGatewayClient,
                new ObjectMapper(),
                "test-webhook-secret"
        );
        authenticateAs("customer@example.com", Role.CUSTOMER);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCanInitiateCashOnDeliveryForOwnOrder() {
        User customer = user(10L, Role.CUSTOMER, true);
        Order order = order(501L, customer, OrderStatus.PLACED, 289.99);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(501L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setMethod(PaymentMethod.COD);

        PaymentResponse response = paymentService.initiatePayment(501L, request);

        assertEquals(501L, response.getOrderId());
        assertEquals(new BigDecimal("289.99"), response.getAmount());
        assertEquals(PaymentMethod.COD, response.getMethod());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        verify(paymentRepository).save(argThat(payment ->
                payment.getOrder() == order
                        && new BigDecimal("289.99").equals(payment.getAmount())
                        && payment.getCurrency().equals("INR")
                        && payment.getMethod() == PaymentMethod.COD
                        && payment.getStatus() == PaymentStatus.PENDING
        ));
        verifyNoInteractions(razorpayGatewayClient);
    }

    @Test
    void customerCannotInitiatePaymentForAnotherCustomersOrder() {
        User currentCustomer = user(10L, Role.CUSTOMER, true);
        User differentCustomer = user(11L, Role.CUSTOMER, true);
        Order order = order(502L, differentCustomer, OrderStatus.PLACED, 150.0);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(currentCustomer));
        when(orderRepository.findByIdForUpdate(502L)).thenReturn(Optional.of(order));

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setMethod(PaymentMethod.COD);

        assertThrows(AccessDeniedException.class,
                () -> paymentService.initiatePayment(502L, request));

        verify(paymentRepository, never()).findByOrderId(anyLong());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void paymentCannotBeInitiatedForCancelledOrder() {
        User customer = user(10L, Role.CUSTOMER, true);
        Order order = order(503L, customer, OrderStatus.CANCELLED, 150.0);
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(503L)).thenReturn(Optional.of(order));

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setMethod(PaymentMethod.COD);

        assertThrows(BadRequestException.class,
                () -> paymentService.initiatePayment(503L, request));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void webhookRejectsAnInvalidSignatureBeforeReadingPaymentRecords() {
        assertThrows(BadRequestException.class,
                () -> paymentService.handleRazorpayWebhook("{}", "invalid-signature"));

        verify(paymentRepository, never()).findByGatewayOrderId(anyString());
    }

    private void authenticateAs(String email, Role role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        "test-credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User user(Long id, Role role, boolean active) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(active);
        when(user.getEmail()).thenReturn("user-" + id + "@example.com");
        return user;
    }

    private Order order(Long id, User customer, OrderStatus status, double total) {
        Order order = new Order();
        order.setId(id);
        order.setCustomer(customer);
        order.setStatus(status);
        order.setTotalAmount(total);
        return order;
    }
}
