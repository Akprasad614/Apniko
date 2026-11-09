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
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.PaymentRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RazorpayGatewayClient razorpayGatewayClient;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            RazorpayGatewayClient razorpayGatewayClient,
            ObjectMapper objectMapper,
            @Value("${RAZORPAY_WEBHOOK_SECRET:}") String webhookSecret) {

        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.razorpayGatewayClient = razorpayGatewayClient;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    @Transactional
    public PaymentResponse initiatePayment(
            Long orderId,
            InitiatePaymentRequest request) {

        User customer = getCurrentUser();
        if (customer.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Only customers can initiate payment");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getCustomer() == null
                || !order.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("You cannot pay for another customer's order");
        }

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException(
                    "Payment cannot be initiated for a cancelled or delivered order"
            );
        }

        BigDecimal amount = BigDecimal.valueOf(order.getTotalAmount())
                .setScale(2, RoundingMode.HALF_UP);

        if (amount.signum() <= 0) {
            throw new BadRequestException("Order amount must be greater than zero");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(Payment::new);

        if (payment.getId() != null) {
            if (payment.getStatus() == PaymentStatus.PAID
                    || payment.getStatus() == PaymentStatus.REFUND_PENDING
                    || payment.getStatus() == PaymentStatus.REFUNDED) {
                throw new BadRequestException("This order already has a completed payment");
            }

            if (payment.getStatus() == PaymentStatus.PENDING) {
                if (payment.getMethod() != request.getMethod()) {
                    throw new BadRequestException(
                            "A pending payment already exists with a different payment method"
                    );
                }

                if (request.getMethod() == PaymentMethod.COD
                        || payment.getGatewayOrderId() != null) {
                    return PaymentResponse.from(payment, publicKeyFor(payment));
                }
            }
        }

        payment.setOrder(order);
        payment.setAmount(amount);
        payment.setCurrency("INR");
        payment.setMethod(request.getMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setGatewayOrderId(null);
        payment.setGatewayPaymentId(null);
        payment.setGatewayRefundId(null);
        payment.setPaidAt(null);

        payment = paymentRepository.save(payment);

        if (request.getMethod() == PaymentMethod.COD) {
            return PaymentResponse.from(payment, null);
        }

        long amountInPaise = RazorpayGatewayClient.toPaise(amount);
        if (amountInPaise < 100) {
            throw new BadRequestException(
                    "Razorpay online payments require an amount of at least INR 1.00"
            );
        }

        JsonNode gatewayOrder = razorpayGatewayClient.createOrder(
                amountInPaise,
                "lc-payment-" + payment.getId(),
                order.getId(),
                payment.getId()
        );

        String gatewayOrderId = gatewayOrder.path("id").asText("");
        if (gatewayOrderId.isBlank()) {
            throw new BadRequestException(
                    "Payment provider did not return an order ID"
            );
        }

        payment.setGatewayOrderId(gatewayOrderId);
        paymentRepository.save(payment);

        return PaymentResponse.from(payment, razorpayGatewayClient.getKeyId());
    }

    @Transactional
    public PaymentResponse verifyPayment(VerifyPaymentRequest request) {

        User currentUser = getCurrentUser();
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        ensurePaymentOwnerOrAdmin(payment, currentUser);

        if (payment.getMethod() != PaymentMethod.RAZORPAY) {
            throw new BadRequestException("This payment does not use Razorpay");
        }

        if (!request.getRazorpayOrderId().equals(payment.getGatewayOrderId())) {
            throw new BadRequestException("Razorpay order ID does not match this payment");
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            if (request.getRazorpayPaymentId().equals(payment.getGatewayPaymentId())) {
                return PaymentResponse.from(payment, razorpayGatewayClient.getKeyId());
            }
            throw new BadRequestException("A different payment is already recorded for this order");
        }

        verifyCheckoutSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        JsonNode gatewayPayment = razorpayGatewayClient.fetchPayment(
                request.getRazorpayPaymentId()
        );

        boolean captured = gatewayPayment.path("captured").asBoolean(false)
                || "captured".equalsIgnoreCase(gatewayPayment.path("status").asText());

        long expectedAmount = RazorpayGatewayClient.toPaise(payment.getAmount());
        long actualAmount = gatewayPayment.path("amount").asLong(-1);
        String actualCurrency = gatewayPayment.path("currency").asText("");
        String actualOrderId = gatewayPayment.path("order_id").asText("");

        if (!captured
                || actualAmount != expectedAmount
                || !"INR".equalsIgnoreCase(actualCurrency)
                || !payment.getGatewayOrderId().equals(actualOrderId)) {
            throw new BadRequestException(
                    "Payment is not captured or its amount/order details do not match"
            );
        }

        recordCapturedPayment(payment, request.getRazorpayPaymentId());
        return PaymentResponse.from(paymentRepository.save(payment), razorpayGatewayClient.getKeyId());
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForOrder(Long orderId) {
        User currentUser = getCurrentUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (currentUser.getRole() != Role.ADMIN
                && (order.getCustomer() == null
                || !order.getCustomer().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("You cannot view this payment");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for this order"));

        return PaymentResponse.from(payment, publicKeyFor(payment));
    }

    @Transactional
    public PaymentResponse collectCashOnDelivery(Long paymentId) {
        User actor = getCurrentUser();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getMethod() != PaymentMethod.COD) {
            throw new BadRequestException("Only COD payments can be collected manually");
        }

        Order order = payment.getOrder();
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isShopOwner = actor.getRole() == Role.SHOPKEEPER
                && order.getShop() != null
                && order.getShop().getOwner() != null
                && order.getShop().getOwner().getId().equals(actor.getId());
        boolean isAssignedPartner = actor.getRole() == Role.DELIVERY_PARTNER
                && order.getDeliveryPartner() != null
                && order.getDeliveryPartner().getId().equals(actor.getId());

        // Authorize before returning an already-paid record. Otherwise any
        // authenticated user who guesses a payment ID could read its details.
        if (!isAdmin && !isShopOwner && !isAssignedPartner) {
            throw new AccessDeniedException("You are not authorized to record this COD collection");
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentResponse.from(payment, null);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("This COD payment is not pending");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException(
                    "COD can be marked collected only after the order is delivered"
            );
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        return PaymentResponse.from(paymentRepository.save(payment), null);
    }

    @Transactional
    public PaymentResponse refundPaymentByAdmin(Long paymentId) {
        User admin = getCurrentUser();
        if (admin.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators can process refunds");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentResponse.from(payment, null);
        }
        if (payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            throw new BadRequestException("Only payments marked REFUND_PENDING can be refunded");
        }

        if (payment.getMethod() == PaymentMethod.COD) {
            throw new BadRequestException(
                    "COD refunds must be completed outside the gateway. Confirm the cash refund manually."
            );
        }
        if (payment.getGatewayPaymentId() == null || payment.getGatewayPaymentId().isBlank()) {
            throw new BadRequestException("Gateway payment ID is missing; refund cannot be initiated");
        }
        if (payment.getGatewayRefundId() != null) {
            return PaymentResponse.from(payment, null);
        }

        JsonNode refund = razorpayGatewayClient.createFullRefund(
                payment.getGatewayPaymentId(),
                payment.getOrder().getId()
        );
        String refundId = refund.path("id").asText("");
        if (refundId.isBlank()) {
            throw new BadRequestException("Payment provider did not return a refund ID");
        }

        payment.setGatewayRefundId(refundId);
        // Keep REFUND_PENDING until Razorpay confirms refund.processed by webhook.
        return PaymentResponse.from(paymentRepository.save(payment), null);
    }

    @Transactional
    public PaymentResponse confirmCodRefundByAdmin(Long paymentId) {
        User admin = getCurrentUser();
        if (admin.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators can confirm COD refunds");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getMethod() != PaymentMethod.COD
                || payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            throw new BadRequestException("This payment is not awaiting a COD refund");
        }

        // The administrator should call this only after the cash refund has actually been paid.
        payment.setStatus(PaymentStatus.REFUNDED);
        return PaymentResponse.from(paymentRepository.save(payment), null);
    }

    @Transactional
    public void handleRazorpayWebhook(String rawBody, String signature) {
        if (webhookSecret.isBlank()) {
            throw new BadRequestException("Razorpay webhook secret is not configured");
        }
        if (signature == null || signature.isBlank()
                || !constantTimeEquals(hmacSha256(rawBody, webhookSecret), signature)) {
            throw new BadRequestException("Invalid Razorpay webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText("");

            if ("refund.processed".equals(event)) {
                JsonNode refundEntity = root.path("payload")
                        .path("refund")
                        .path("entity");
                String refundedPaymentId = refundEntity.path("payment_id").asText("");
                String refundId = refundEntity.path("id").asText("");
                long refundedAmount = refundEntity.path("amount").asLong(-1);
                String refundCurrency = refundEntity.path("currency").asText("");
                if (!refundedPaymentId.isBlank()) {
                    paymentRepository.findByGatewayPaymentId(refundedPaymentId)
                            .ifPresent(refundPayment -> {
                                boolean fullRefund = refundedAmount
                                        == RazorpayGatewayClient.toPaise(refundPayment.getAmount())
                                        && "INR".equalsIgnoreCase(refundCurrency);
                                boolean matchingRefund = refundPayment.getGatewayRefundId() == null
                                        || refundPayment.getGatewayRefundId().equals(refundId);
                                if (fullRefund
                                        && matchingRefund
                                        && (refundPayment.getStatus() == PaymentStatus.REFUND_PENDING
                                        || refundPayment.getStatus() == PaymentStatus.PAID)) {
                                    refundPayment.setStatus(PaymentStatus.REFUNDED);
                                    if (!refundId.isBlank()) {
                                        refundPayment.setGatewayRefundId(refundId);
                                    }
                                    paymentRepository.save(refundPayment);
                                }
                            });
                }
                return;
            }

            JsonNode paymentEntity = root.path("payload")
                    .path("payment")
                    .path("entity");

            if (paymentEntity.isMissingNode() || paymentEntity.isNull()) {
                return;
            }

            String gatewayOrderId = paymentEntity.path("order_id").asText("");
            String gatewayPaymentId = paymentEntity.path("id").asText("");
            if (gatewayOrderId.isBlank() || gatewayPaymentId.isBlank()) {
                return;
            }

            Optional<Payment> optionalPayment = paymentRepository.findByGatewayOrderId(gatewayOrderId);
            if (optionalPayment.isEmpty()) {
                return;
            }

            Payment payment = optionalPayment.get();
            long eventAmount = paymentEntity.path("amount").asLong(-1);
            String eventCurrency = paymentEntity.path("currency").asText("");

            if (eventAmount != RazorpayGatewayClient.toPaise(payment.getAmount())
                    || !"INR".equalsIgnoreCase(eventCurrency)) {
                throw new BadRequestException("Webhook payment amount or currency does not match");
            }

            if ("payment.captured".equals(event)) {
                if (payment.getStatus() != PaymentStatus.PAID
                        && payment.getStatus() != PaymentStatus.REFUNDED) {
                    recordCapturedPayment(payment, gatewayPaymentId);
                    paymentRepository.save(payment);
                }
            } else if ("payment.failed".equals(event)) {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setGatewayPaymentId(gatewayPaymentId);
                    paymentRepository.save(payment);
                }
            } else if ("payment.refunded".equals(event)) {
                long totalRefunded = paymentEntity.path("amount_refunded").asLong(0);
                if (totalRefunded >= RazorpayGatewayClient.toPaise(payment.getAmount())
                        && (payment.getStatus() == PaymentStatus.REFUND_PENDING
                        || payment.getStatus() == PaymentStatus.PAID)) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                }
            }
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException("Unable to process Razorpay webhook payload");
        }
    }

    private void verifyCheckoutSignature(
            String razorpayOrderId,
            String razorpayPaymentId,
            String suppliedSignature) {

        String secret = razorpayGatewayClient.getKeySecret();
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("Razorpay is not configured");
        }

        String expected = hmacSha256(
                razorpayOrderId + "|" + razorpayPaymentId,
                secret
        );
        if (!constantTimeEquals(expected, suppliedSignature)) {
            throw new BadRequestException("Invalid Razorpay payment signature");
        }
    }

    private void recordCapturedPayment(Payment payment, String gatewayPaymentId) {
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setPaidAt(LocalDateTime.now());
        if (payment.getOrder().getStatus() == OrderStatus.CANCELLED) {
            payment.setStatus(PaymentStatus.REFUND_PENDING);
        } else {
            payment.setStatus(PaymentStatus.PAID);
        }
    }

    private String publicKeyFor(Payment payment) {
        return payment.getMethod() == PaymentMethod.RAZORPAY
                ? razorpayGatewayClient.getKeyId()
                : null;
    }

    private User getCurrentUser() {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            throw new BadRequestException("User is not authenticated");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.isActive()) {
            throw new AccessDeniedException("User account is inactive");
        }
        return user;
    }

    private void ensurePaymentOwnerOrAdmin(Payment payment, User user) {
        if (user.getRole() != Role.ADMIN
                && (payment.getOrder().getCustomer() == null
                || !payment.getOrder().getCustomer().getId().equals(user.getId()))) {
            throw new AccessDeniedException("You cannot access this payment");
        }
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BadRequestException("Unable to verify payment signature");
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                supplied.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }
}
