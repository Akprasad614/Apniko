package com.localcart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localcart.dto.InitiatePaymentRequest;
import com.localcart.dto.PaymentResponse;
import com.localcart.dto.VerifyPaymentRequest;
import com.localcart.entity.Order;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Payment;
import com.localcart.entity.PaymentMethod;
import com.localcart.entity.PaymentStatus;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceWorkflowTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String CHECKOUT_SECRET = "checkout-secret";

    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private RazorpayGatewayClient gateway;
    private ObjectMapper mapper;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        gateway = mock(RazorpayGatewayClient.class);
        mapper = new ObjectMapper();
        service = new PaymentService(
                paymentRepository, orderRepository, userRepository, gateway, mapper, WEBHOOK_SECRET
        );
        authenticate(CUSTOMER_EMAIL, Role.CUSTOMER);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void initiationRequiresAnAuthenticatedUser() {
        SecurityContextHolder.clearContext();
        InitiatePaymentRequest request = initiate(PaymentMethod.COD);
        assertThrows(BadRequestException.class, () -> service.initiatePayment(1L, request));
        verifyNoInteractions(userRepository, orderRepository, paymentRepository);
    }

    @Test
    void onlyCustomersCanInitiatePayments() {
        stubCurrentUser(Role.ADMIN, 99L, true);
        assertThrows(AccessDeniedException.class,
                () -> service.initiatePayment(1L, initiate(PaymentMethod.COD)));
        verifyNoInteractions(orderRepository, paymentRepository);
    }

    @Test
    void inactiveAccountCannotInitiatePayment() {
        stubCurrentUser(Role.CUSTOMER, 10L, false);
        assertThrows(AccessDeniedException.class,
                () -> service.initiatePayment(1L, initiate(PaymentMethod.COD)));
        verifyNoInteractions(orderRepository, paymentRepository);
    }

    @Test
    void missingOrderIsReported() {
        stubCurrentUser(Role.CUSTOMER, 10L, true);
        when(orderRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.initiatePayment(404L, initiate(PaymentMethod.COD)));
    }

    @Test
    void zeroOrNegativeOrderAmountIsRejected() {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        when(orderRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(order(5L, customer, OrderStatus.PLACED, 0.0)));
        assertThrows(BadRequestException.class,
                () -> service.initiatePayment(5L, initiate(PaymentMethod.COD)));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void deliveredOrderCannotStartAnotherPayment() {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        when(orderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order(6L, customer, OrderStatus.DELIVERED, 100.0)));
        assertThrows(BadRequestException.class,
                () -> service.initiatePayment(6L, initiate(PaymentMethod.COD)));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void existingPendingCodPaymentIsReturnedWithoutCreatingAnother() {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(7L, customer, OrderStatus.PLACED, 125.0);
        Payment existing = payment(70L, order, PaymentMethod.COD, PaymentStatus.PENDING, "", "");
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.of(existing));

        PaymentResponse response = service.initiatePayment(7L, initiate(PaymentMethod.COD));

        assertEquals(70L, response.getId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
        verifyNoInteractions(gateway);
    }

    @Test
    void pendingPaymentCannotSwitchPaymentMethod() {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(8L, customer, OrderStatus.PLACED, 125.0);
        Payment existing = payment(80L, order, PaymentMethod.COD, PaymentStatus.PENDING, "", "");
        when(orderRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(8L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> service.initiatePayment(8L, initiate(PaymentMethod.RAZORPAY)));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void completedPaymentCannotBeInitiatedAgain() {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(9L, customer, OrderStatus.PLACED, 125.0);
        Payment existing = payment(90L, order, PaymentMethod.COD, PaymentStatus.PAID, "", "");
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(9L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> service.initiatePayment(9L, initiate(PaymentMethod.COD)));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void razorpayInitiationCreatesGatewayOrderAndReturnsPublicKey() throws Exception {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(10L, customer, OrderStatus.PLACED, 250.00);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(100L);
            return saved;
        });
        when(gateway.createOrder(25000L, "lc-payment-100", 10L, 100L))
                .thenReturn(mapper.readTree("{\"id\":\"order_gateway_10\"}"));
        when(gateway.getKeyId()).thenReturn("rzp_test_public_key");

        PaymentResponse response = service.initiatePayment(10L, initiate(PaymentMethod.RAZORPAY));

        assertEquals(PaymentMethod.RAZORPAY, response.getMethod());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals("order_gateway_10", response.getRazorpayOrderId());
        assertEquals("rzp_test_public_key", response.getRazorpayKeyId());
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    void razorpayInitiationRejectsGatewayResponseWithoutOrderId() throws Exception {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(11L, customer, OrderStatus.PLACED, 250.00);
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(11L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });
        when(gateway.createOrder(anyLong(), anyString(), anyLong(), anyLong()))
                .thenReturn(mapper.readTree("{}"));

        assertThrows(BadRequestException.class,
                () -> service.initiatePayment(11L, initiate(PaymentMethod.RAZORPAY)));
    }

    @Test
    void customerCanVerifyCapturedRazorpayPayment() throws Exception {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(12L, customer, OrderStatus.PLACED, 250.00);
        Payment payment = payment(120L, order, PaymentMethod.RAZORPAY,
                PaymentStatus.PENDING, "order_12", "");
        when(paymentRepository.findById(120L)).thenReturn(Optional.of(payment));
        when(gateway.getKeySecret()).thenReturn(CHECKOUT_SECRET);
        when(gateway.getKeyId()).thenReturn("rzp_test_key");
        when(gateway.fetchPayment("pay_12")).thenReturn(mapper.readTree(
                "{\"captured\":true,\"amount\":25000,\"currency\":\"INR\",\"order_id\":\"order_12\"}"
        ));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.verifyPayment(verifyRequest(
                120L, "order_12", "pay_12", checkoutSignature("order_12", "pay_12")
        ));

        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertEquals("pay_12", response.getRazorpayPaymentId());
        assertNotNull(response.getPaidAt());
        verify(paymentRepository).save(payment);
    }

    @Test
    void checkoutVerificationRejectsInvalidSignatureBeforeGatewayLookup() {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(13L, customer, OrderStatus.PLACED, 250.00);
        Payment payment = payment(130L, order, PaymentMethod.RAZORPAY,
                PaymentStatus.PENDING, "order_13", "");
        when(paymentRepository.findById(130L)).thenReturn(Optional.of(payment));
        when(gateway.getKeySecret()).thenReturn(CHECKOUT_SECRET);

        assertThrows(BadRequestException.class, () -> service.verifyPayment(
                verifyRequest(130L, "order_13", "pay_13", "not-a-valid-signature")
        ));
        verify(gateway, never()).fetchPayment(anyString());
    }

    @Test
    void checkoutVerificationRejectsAmountMismatch() throws Exception {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(14L, customer, OrderStatus.PLACED, 250.00);
        Payment payment = payment(140L, order, PaymentMethod.RAZORPAY,
                PaymentStatus.PENDING, "order_14", "");
        when(paymentRepository.findById(140L)).thenReturn(Optional.of(payment));
        when(gateway.getKeySecret()).thenReturn(CHECKOUT_SECRET);
        when(gateway.fetchPayment("pay_14")).thenReturn(mapper.readTree(
                "{\"captured\":true,\"amount\":24900,\"currency\":\"INR\",\"order_id\":\"order_14\"}"
        ));

        assertThrows(BadRequestException.class, () -> service.verifyPayment(
                verifyRequest(140L, "order_14", "pay_14", checkoutSignature("order_14", "pay_14"))
        ));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void capturedPaymentForAlreadyCancelledOrderIsMarkedRefundPending() throws Exception {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(15L, customer, OrderStatus.CANCELLED, 250.00);
        Payment payment = payment(150L, order, PaymentMethod.RAZORPAY,
                PaymentStatus.PENDING, "order_15", "");
        when(paymentRepository.findById(150L)).thenReturn(Optional.of(payment));
        when(gateway.getKeySecret()).thenReturn(CHECKOUT_SECRET);
        when(gateway.fetchPayment("pay_15")).thenReturn(mapper.readTree(
                "{\"status\":\"captured\",\"amount\":25000,\"currency\":\"INR\",\"order_id\":\"order_15\"}"
        ));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.verifyPayment(verifyRequest(
                150L, "order_15", "pay_15", checkoutSignature("order_15", "pay_15")
        ));
        assertEquals(PaymentStatus.REFUND_PENDING, response.getStatus());
    }

    @Test
    void customerCannotReadAnotherCustomersPayment() {
        User customer = stubCurrentUser(Role.CUSTOMER, 10L, true);
        User otherCustomer = user(11L, Role.CUSTOMER, true);
        Order order = order(16L, otherCustomer, OrderStatus.PLACED, 100.00);
        when(orderRepository.findById(16L)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class, () -> service.getPaymentForOrder(16L));
        verify(paymentRepository, never()).findByOrderId(anyLong());
    }

    @Test
    void assignedDeliveryPartnerCanCollectCodAfterDelivery() {
        User partner = stubCurrentUser(Role.DELIVERY_PARTNER, 22L, true);
        Order order = order(17L, user(10L, Role.CUSTOMER, true), OrderStatus.DELIVERED, 80.0);
        order.setDeliveryPartner(partner);
        Payment payment = payment(170L, order, PaymentMethod.COD, PaymentStatus.PENDING, "", "");
        when(paymentRepository.findById(170L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.collectCashOnDelivery(170L);

        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertNotNull(response.getPaidAt());
        verify(paymentRepository).save(payment);
    }

    @Test
    void codCannotBeCollectedBeforeOrderDelivery() {
        User admin = stubCurrentUser(Role.ADMIN, 99L, true);
        Order order = order(18L, user(10L, Role.CUSTOMER, true), OrderStatus.READY, 80.0);
        Payment payment = payment(180L, order, PaymentMethod.COD, PaymentStatus.PENDING, "", "");
        when(paymentRepository.findById(180L)).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class, () -> service.collectCashOnDelivery(180L));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void unauthorizedUserCannotCollectCodEvenWhenPaymentIsAlreadyPaid() {
        stubCurrentUser(Role.CUSTOMER, 10L, true);
        Order order = order(19L, user(10L, Role.CUSTOMER, true), OrderStatus.DELIVERED, 80.0);
        Payment payment = payment(190L, order, PaymentMethod.COD, PaymentStatus.PAID, "", "");
        when(paymentRepository.findById(190L)).thenReturn(Optional.of(payment));

        assertThrows(AccessDeniedException.class, () -> service.collectCashOnDelivery(190L));
    }

    @Test
    void onlyAdminCanStartGatewayRefund() {
        stubCurrentUser(Role.CUSTOMER, 10L, true);
        User orderCustomer = user(10L, Role.CUSTOMER, true);
        Order refundOrder = order(20L, orderCustomer, OrderStatus.CANCELLED, 100.0);
        Payment refundPayment = payment(
                200L, refundOrder, PaymentMethod.RAZORPAY,
                PaymentStatus.REFUND_PENDING, "gw_order", "gw_payment"
        );
        when(paymentRepository.findById(200L)).thenReturn(Optional.of(refundPayment));
        assertThrows(AccessDeniedException.class, () -> service.refundPaymentByAdmin(200L));
        verifyNoInteractions(gateway);
    }

    @Test
    void adminCanStartGatewayRefundAndPaymentRemainsRefundPending() throws Exception {
        stubCurrentUser(Role.ADMIN, 99L, true);
        Order order = order(21L, user(10L, Role.CUSTOMER, true), OrderStatus.CANCELLED, 100.0);
        Payment payment = payment(210L, order, PaymentMethod.RAZORPAY,
                PaymentStatus.REFUND_PENDING, "gw_order", "gw_payment");
        when(paymentRepository.findById(210L)).thenReturn(Optional.of(payment));
        when(gateway.createFullRefund("gw_payment", 21L))
                .thenReturn(mapper.readTree("{\"id\":\"rfnd_21\"}"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.refundPaymentByAdmin(210L);

        assertEquals("rfnd_21", response.getRazorpayRefundId());
        assertEquals(PaymentStatus.REFUND_PENDING, response.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void adminCanConfirmManualCodRefund() {
        stubCurrentUser(Role.ADMIN, 99L, true);
        Payment payment = payment(220L,
                order(22L, user(10L, Role.CUSTOMER, true), OrderStatus.CANCELLED, 100.0),
                PaymentMethod.COD, PaymentStatus.REFUND_PENDING, "", "");
        when(paymentRepository.findById(220L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.confirmCodRefundByAdmin(220L);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void validPaymentFailedWebhookMarksPendingPaymentFailed() {
        Payment payment = payment(230L,
                order(23L, user(10L, Role.CUSTOMER, true), OrderStatus.PLACED, 150.0),
                PaymentMethod.RAZORPAY, PaymentStatus.PENDING, "gw_order_23", "");
        when(paymentRepository.findByGatewayOrderId("gw_order_23")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String body = paymentWebhook("payment.failed", "gw_order_23", "gw_payment_23", 15000, "INR", null);

        service.handleRazorpayWebhook(body, webhookSignature(body));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("gw_payment_23", payment.getGatewayPaymentId());
        verify(paymentRepository).save(payment);
    }

    @Test
    void validPaymentCapturedWebhookMarksPaymentPaid() {
        Payment payment = payment(240L,
                order(24L, user(10L, Role.CUSTOMER, true), OrderStatus.PLACED, 150.0),
                PaymentMethod.RAZORPAY, PaymentStatus.PENDING, "gw_order_24", "");
        when(paymentRepository.findByGatewayOrderId("gw_order_24")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String body = paymentWebhook("payment.captured", "gw_order_24", "gw_payment_24", 15000, "INR", null);

        service.handleRazorpayWebhook(body, webhookSignature(body));

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertNotNull(payment.getPaidAt());
        verify(paymentRepository).save(payment);
    }

    @Test
    void paymentWebhookRejectsAmountOrCurrencyMismatch() {
        Payment payment = payment(250L,
                order(25L, user(10L, Role.CUSTOMER, true), OrderStatus.PLACED, 150.0),
                PaymentMethod.RAZORPAY, PaymentStatus.PENDING, "gw_order_25", "");
        when(paymentRepository.findByGatewayOrderId("gw_order_25")).thenReturn(Optional.of(payment));
        String body = paymentWebhook("payment.captured", "gw_order_25", "gw_payment_25", 14900, "INR", null);

        assertThrows(BadRequestException.class,
                () -> service.handleRazorpayWebhook(body, webhookSignature(body)));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void refundProcessedWebhookMarksMatchingFullRefundAsRefunded() {
        Payment payment = payment(260L,
                order(26L, user(10L, Role.CUSTOMER, true), OrderStatus.CANCELLED, 150.0),
                PaymentMethod.RAZORPAY, PaymentStatus.REFUND_PENDING, "gw_order_26", "gw_payment_26");
        payment.setGatewayRefundId("rfnd_26");
        when(paymentRepository.findByGatewayPaymentId("gw_payment_26")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String body = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{"
                + "\"payment_id\":\"gw_payment_26\",\"id\":\"rfnd_26\",\"amount\":15000,\"currency\":\"INR\"}}}}";

        service.handleRazorpayWebhook(body, webhookSignature(body));

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(paymentRepository).save(payment);
    }

    private InitiatePaymentRequest initiate(PaymentMethod method) {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setMethod(method);
        return request;
    }

    private VerifyPaymentRequest verifyRequest(Long id, String orderId, String paymentId, String signature) {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setPaymentId(id);
        request.setRazorpayOrderId(orderId);
        request.setRazorpayPaymentId(paymentId);
        request.setRazorpaySignature(signature);
        return request;
    }

    private User stubCurrentUser(Role role, Long id, boolean active) {
        User user = user(id, role, active);
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    private User user(Long id, Role role, boolean active) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(active);
        when(user.getEmail()).thenReturn("user-" + id + "@example.com");
        return user;
    }

    private void authenticate(String email, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "test", List.of(
                        new SimpleGrantedAuthority("ROLE_" + role.name())
                ))
        );
    }

    private Order order(Long id, User customer, OrderStatus status, double total) {
        Order order = new Order();
        order.setId(id);
        order.setCustomer(customer);
        order.setStatus(status);
        order.setTotalAmount(total);
        return order;
    }

    private Payment payment(Long id, Order order, PaymentMethod method, PaymentStatus status,
                            String gatewayOrderId, String gatewayPaymentId) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrder(order);
        payment.setAmount(new java.math.BigDecimal("" + order.getTotalAmount()).setScale(2));
        payment.setCurrency("INR");
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setGatewayOrderId(gatewayOrderId);
        payment.setGatewayPaymentId(gatewayPaymentId);
        return payment;
    }

    private String checkoutSignature(String orderId, String paymentId) {
        return hmac(orderId + "|" + paymentId, CHECKOUT_SECRET);
    }

    private String webhookSignature(String body) {
        return hmac(body, WEBHOOK_SECRET);
    }

    private String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate test signature", exception);
        }
    }

    private String paymentWebhook(String event, String orderId, String paymentId,
                                  long amount, String currency, Long amountRefunded) {
        StringBuilder entity = new StringBuilder()
                .append("{\"order_id\":\"").append(orderId)
                .append("\",\"id\":\"").append(paymentId)
                .append("\",\"amount\":").append(amount)
                .append(",\"currency\":\"").append(currency).append("\"");
        if (amountRefunded != null) {
            entity.append(",\"amount_refunded\":").append(amountRefunded);
        }
        entity.append("}");
        return "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":"
                + entity + "}}}";
    }
}
