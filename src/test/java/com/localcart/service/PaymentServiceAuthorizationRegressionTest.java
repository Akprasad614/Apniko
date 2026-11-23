package com.localcart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localcart.dto.VerifyPaymentRequest;
import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Payment;
import com.localcart.entity.PaymentMethod;
import com.localcart.entity.PaymentStatus;
import com.localcart.entity.Role;
import com.localcart.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PaymentServiceAuthorizationRegressionTest {

    private static final String CALLER_EMAIL = "caller@example.com";
    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private RazorpayGatewayClient gateway;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        gateway = mock(RazorpayGatewayClient.class);
        service = new PaymentService(
                paymentRepository, orderRepository, userRepository, gateway,
                new ObjectMapper(), "test-webhook-secret"
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCannotVerifyAnotherCustomersPaymentEvenWithValidLookingGatewayIds() {
        User caller = user(10L, Role.CUSTOMER, true);
        User actualOwner = user(20L, Role.CUSTOMER, true);
        Order order = new Order();
        order.setCustomer(actualOwner);
        order.setStatus(OrderStatus.PLACED);
        order.setTotalAmount(250.0);
        Payment payment = new Payment();
        payment.setId(900L);
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("250.00"));
        payment.setMethod(PaymentMethod.RAZORPAY);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setGatewayOrderId("gateway-order-900");

        authenticate(CALLER_EMAIL, Role.CUSTOMER);
        when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.of(caller));
        when(paymentRepository.findById(900L)).thenReturn(Optional.of(payment));

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setPaymentId(900L);
        request.setRazorpayOrderId("gateway-order-900");
        request.setRazorpayPaymentId("gateway-payment-900");
        request.setRazorpaySignature("syntactically-valid-looking-signature");

        assertThrows(AccessDeniedException.class, () -> service.verifyPayment(request));

        verifyNoInteractions(gateway);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void inactiveAccountCannotReadPaymentDetails() {
        User inactiveCustomer = user(10L, Role.CUSTOMER, false);
        authenticate(CALLER_EMAIL, Role.CUSTOMER);
        when(userRepository.findByEmail(CALLER_EMAIL))
                .thenReturn(Optional.of(inactiveCustomer));

        assertThrows(AccessDeniedException.class, () -> service.getPaymentForOrder(900L));

        verifyNoInteractions(orderRepository, paymentRepository, gateway);
    }

    private User user(Long id, Role role, boolean active) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(active);
        return user;
    }

    private void authenticate(String email, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email,
                        "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                )
        );
    }
}
