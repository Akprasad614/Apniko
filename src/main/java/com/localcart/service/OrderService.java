package com.localcart.service;

import com.localcart.dto.CreateOrderRequest;
import com.localcart.dto.OrderItemRequest;
import com.localcart.dto.OrderStatusHistoryResponse;
import com.localcart.entity.Cart;
import com.localcart.entity.DeliveryMode;
import com.localcart.entity.Order;
import com.localcart.entity.OrderDeliveryAddress;
import com.localcart.entity.OrderItem;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.OrderStatusHistory;
import com.localcart.entity.PaymentStatus;
import com.localcart.entity.InventoryMovement;
import com.localcart.entity.InventoryMovementType;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.CartRepository;
import com.localcart.repository.OrderItemRepository;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.OrderStatusHistoryRepository;
import com.localcart.repository.PaymentRepository;
import com.localcart.repository.InventoryMovementRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShopRepository shopRepository;
    private final ShopInventoryRepository shopInventoryRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;

    public OrderService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            PaymentRepository paymentRepository,
            InventoryMovementRepository inventoryMovementRepository,
            OrderItemRepository orderItemRepository,
            ShopRepository shopRepository,
            ShopInventoryRepository shopInventoryRepository,
            UserRepository userRepository,
            CartRepository cartRepository) {

        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.orderItemRepository = orderItemRepository;
        this.shopRepository = shopRepository;
        this.shopInventoryRepository = shopInventoryRepository;
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
    }

    // =========================================================
    // CUSTOMER ORDER CREATION
    // =========================================================

    @Transactional
    public Optional<Order> createOrder(
            CreateOrderRequest request) {

        Optional<Shop> shopOptional =
                shopRepository.findById(request.getShopId());

        if (shopOptional.isEmpty()) {
            return Optional.empty();
        }

        String email =
                SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return Optional.empty();
        }

        Optional<User> customerOptional =
                userRepository.findByEmail(email);

        if (customerOptional.isEmpty()) {
            return Optional.empty();
        }

        Shop shop = shopOptional.get();
        User customer = customerOptional.get();

        if (!shop.isActive()
                || !customer.isActive()) {

            return Optional.empty();
        }

        if (customer.getRole() != Role.CUSTOMER) {
            return Optional.empty();
        }

        return buildOrder(
                customer,
                shop,
                request.getItems(),
                request.getDeliveryMode(),
                null
        );
    }

    @Transactional
    public Order checkoutCart(String email, Long shopId, DeliveryMode deliveryMode) {
        return checkoutCart(email, shopId, deliveryMode, null);
    }

    @Transactional
    public Order checkoutCart(
            String email,
            Long shopId,
            DeliveryMode deliveryMode,
            OrderDeliveryAddress deliveryAddress) {

        User customer = getCustomer(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        if (!shop.isActive()) {
            throw new BadRequestException("Shop is not active");
        }

        Cart cart = cartRepository.findByCustomerIdAndShopId(customer.getId(), shopId)
                .orElseThrow(() -> new BadRequestException("Cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        if (deliveryMode != DeliveryMode.CUSTOMER_PICKUP && deliveryAddress == null) {
            throw new BadRequestException("Select a saved delivery address before placing this order");
        }

        List<OrderItemRequest> items = cart.getItems().stream().map(item -> {
            OrderItemRequest request = new OrderItemRequest();
            request.setProductId(item.getProduct().getId());
            request.setQuantity(item.getQuantity());
            return request;
        }).toList();

        Order order = buildOrder(customer, shop, items, deliveryMode, deliveryAddress)
                .orElseThrow(() -> new BadRequestException("Unable to create order"));

        cart.getItems().clear();
        cartRepository.save(cart);
        return order;
    }

    private Optional<Order> buildOrder(
            User customer,
            Shop shop,
            List<OrderItemRequest> items,
            DeliveryMode deliveryMode,
            OrderDeliveryAddress deliveryAddress) {

        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }

        Order order = new Order();

        order.setShop(shop);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PLACED);
        order.setDeliveryMode(deliveryMode);
        order.setDeliveryAddress(deliveryAddress);

        order = orderRepository.save(order);
        recordStatusChange(
                order,
                null,
                OrderStatus.PLACED,
                customer,
                "Order placed"
        );

        double totalAmount = 0;

        for (OrderItemRequest itemRequest : items) {

            ShopInventory inventory =
                    shopInventoryRepository
                            .findByShopIdAndProductIdForUpdate(
                                    shop.getId(),
                                    itemRequest.getProductId()
                            )
                            .orElse(null);

            if (inventory == null) {
                throw new BadRequestException(
                        "Product is not available in this shop"
                );
            }

            if (!inventory.isAvailable()
                    || inventory.getStockQuantity()
                    < itemRequest.getQuantity()) {

                throw new BadRequestException(
                        "Insufficient stock for "
                                + inventory.getProduct().getName()
                );
            }

            double price = inventory.getPrice();

            OrderItem orderItem = new OrderItem();

            orderItem.setOrder(order);
            orderItem.setProduct(
                    inventory.getProduct()
            );
            orderItem.setQuantity(
                    itemRequest.getQuantity()
            );
            orderItem.setPrice(price);

            order.getItems().add(orderItem);

            totalAmount +=
                    price * itemRequest.getQuantity();

            int previousQuantity = inventory.getStockQuantity();
            inventory.setStockQuantity(
                    previousQuantity - itemRequest.getQuantity()
            );

            inventory.setAvailable(
                    inventory.getStockQuantity() > 0
            );

            shopInventoryRepository.save(inventory);
            recordInventoryMovement(
                    inventory,
                    InventoryMovementType.ORDER_DEDUCTION,
                    previousQuantity,
                    inventory.getStockQuantity(),
                    customer,
                    "Stock deducted for order " + order.getId()
            );
            orderItemRepository.save(orderItem);
        }

        order.setTotalAmount(totalAmount);

        return Optional.of(
                orderRepository.save(order)
        );
    }

    private User getCustomer(String email) {

        if (email == null) {
            throw new BadRequestException(
                    "User is not authenticated"
            );
        }

        User customer =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User not found"
                                ));

        if (customer.getRole() != Role.CUSTOMER) {
            throw new BadRequestException(
                    "Only a customer can place an order"
            );
        }

        if (!customer.isActive()) {
            throw new BadRequestException(
                    "Customer account is inactive"
            );
        }

        return customer;
    }

    // =========================================================
    // GENERAL ORDER GETTERS
    // =========================================================

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<List<Order>> getOrdersByShop(
            Long shopId) {

        if (!shopRepository.existsById(shopId)) {
            return Optional.empty();
        }

        return Optional.of(
                orderRepository.findByShopId(shopId)
        );
    }

    public Optional<List<Order>> getOrdersByCustomer(
            Long customerId) {

        if (!userRepository.existsById(customerId)) {
            return Optional.empty();
        }

        return Optional.of(
                orderRepository.findByCustomerId(customerId)
        );
    }

    // =========================================================
    // SHOPKEEPER ORDER STATUS
    // =========================================================

    @Transactional
    public Optional<Order> updateShopkeeperOrderStatus(
            Long orderId,
            OrderStatus newStatus,
            String email) {

        User shopkeeper = getShopkeeper(email);

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null) {
            return Optional.empty();
        }

        if (order.getShop() == null
                || order.getShop().getOwner() == null
                || !order.getShop().getOwner().getId().equals(shopkeeper.getId())) {
            throw new BadRequestException(
                    "You are not authorized to update this order"
            );
        }

        OrderStatus currentStatus = order.getStatus();

        if (!isValidShopkeeperStatusChange(currentStatus, newStatus)) {
            return Optional.empty();
        }

        recordStatusChange(
                order,
                currentStatus,
                newStatus,
                shopkeeper,
                "Order status updated by shopkeeper"
        );

        order.setStatus(newStatus);
        return Optional.of(orderRepository.save(order));
    }

    private boolean isValidShopkeeperStatusChange(
            OrderStatus currentStatus,
            OrderStatus newStatus) {

        if (currentStatus == null ||
                newStatus == null) {

            return false;
        }

        if (currentStatus == OrderStatus.PLACED) {
            return newStatus == OrderStatus.ACCEPTED;
        }

        if (currentStatus == OrderStatus.ACCEPTED) {
            return newStatus == OrderStatus.PREPARING;
        }

        if (currentStatus == OrderStatus.PREPARING) {
            return newStatus == OrderStatus.READY;
        }

        return false;
    }

    // =========================================================
    // DELIVERY PARTNER
    // =========================================================

    public List<Order> getAvailableDeliveryOrders() {

        return orderRepository
                .findByStatusAndDeliveryPartnerIsNull(
                        OrderStatus.READY
                )
                .stream()
                .filter(order ->
                        order.getDeliveryMode()
                                == DeliveryMode.PLATFORM_DELIVERY
                )
                .toList();
    }

    public List<Order> getMyDeliveryOrders(
            String email) {

        User deliveryPartner =
                getDeliveryPartner(email);

        return orderRepository.findByDeliveryPartnerId(
                deliveryPartner.getId()
        );
    }

    @Transactional
    public Order acceptDeliveryOrder(
            Long orderId,
            String email) {

        User deliveryPartner =
                getDeliveryPartner(email);

        Order order =
                orderRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Order not found"
                                ));

        if (order.getStatus() != OrderStatus.READY) {
            throw new BadRequestException(
                    "Only READY orders can be accepted"
            );
        }

        if (order.getDeliveryMode()
                != DeliveryMode.PLATFORM_DELIVERY) {

            throw new BadRequestException(
                    "This order is not assigned to platform delivery"
            );
        }

        if (order.getDeliveryPartner() != null) {
            throw new BadRequestException(
                    "Order is already assigned to a delivery partner"
            );
        }

        order.setDeliveryPartner(deliveryPartner);

        return orderRepository.save(order);
    }

    @Transactional
    public Order pickupOrder(
            Long orderId,
            String email) {

        User deliveryPartner =
                getDeliveryPartner(email);

        Order order =
                orderRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Order not found"
                                ));

        verifyDeliveryPartnerOwnership(
                order,
                deliveryPartner
        );

        if (order.getStatus() != OrderStatus.READY) {
            throw new BadRequestException(
                    "Only READY orders can be picked up"
            );
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        recordStatusChange(
                order,
                previousStatus,
                OrderStatus.OUT_FOR_DELIVERY,
                deliveryPartner,
                "Delivery partner picked up the order"
        );

        return orderRepository.save(order);
    }

    @Transactional
    public Order deliverOrder(
            Long orderId,
            String email) {

        User deliveryPartner =
                getDeliveryPartner(email);

        Order order =
                orderRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Order not found"
                                ));

        verifyDeliveryPartnerOwnership(
                order,
                deliveryPartner
        );

        if (order.getStatus()
                != OrderStatus.OUT_FOR_DELIVERY) {

            throw new BadRequestException(
                    "Only OUT_FOR_DELIVERY orders can be delivered"
            );
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.DELIVERED);
        recordStatusChange(
                order,
                previousStatus,
                OrderStatus.DELIVERED,
                deliveryPartner,
                "Order delivered successfully"
        );

        return orderRepository.save(order);
    }

    private User getShopkeeper(String email) {

        if (email == null) {
            throw new BadRequestException(
                    "User is not authenticated"
            );
        }

        User shopkeeper = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        if (shopkeeper.getRole() != Role.SHOPKEEPER) {
            throw new BadRequestException(
                    "Only a shopkeeper can perform this action"
            );
        }

        if (!shopkeeper.isActive()) {
            throw new BadRequestException(
                    "Shopkeeper account is inactive"
            );
        }

        return shopkeeper;
    }

    private User getDeliveryPartner(
            String email) {

        if (email == null) {
            throw new BadRequestException(
                    "User is not authenticated"
            );
        }

        User deliveryPartner =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User not found"
                                ));

        if (deliveryPartner.getRole()
                != Role.DELIVERY_PARTNER) {

            throw new BadRequestException(
                    "Only a delivery partner can perform this action"
            );
        }

        if (!deliveryPartner.isActive()) {
            throw new BadRequestException(
                    "Delivery partner account is inactive"
            );
        }

        return deliveryPartner;
    }

    private void verifyDeliveryPartnerOwnership(
            Order order,
            User deliveryPartner) {

        if (order.getDeliveryPartner() == null) {
            throw new BadRequestException(
                    "Order is not assigned to a delivery partner"
            );
        }

        if (!order.getDeliveryPartner()
                .getId()
                .equals(deliveryPartner.getId())) {

            throw new BadRequestException(
                    "This order is assigned to another delivery partner"
            );
        }
    }

    // =========================================================
    // CUSTOMER AND SHOPKEEPER CANCELLATION
    // =========================================================

    @Transactional
    public Optional<Order> cancelOrderByCustomer(
            Long orderId,
            String email,
            String reason) {

        User customer = getCustomer(email);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null) {
            return Optional.empty();
        }

        if (order.getCustomer() == null
                || !order.getCustomer().getId().equals(customer.getId())) {
            throw new BadRequestException(
                    "You are not authorized to cancel this order"
            );
        }

        if (order.getStatus() != OrderStatus.PLACED) {
            return Optional.empty();
        }

        return cancelAndRestock(
                order,
                customer,
                normalizeCancellationReason(reason, "Cancelled by customer")
        );
    }

    @Transactional
    public Optional<Order> rejectOrderByShopkeeper(
            Long orderId,
            String email,
            String reason) {

        User shopkeeper = getShopkeeper(email);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null) {
            return Optional.empty();
        }

        if (order.getShop() == null
                || order.getShop().getOwner() == null
                || !order.getShop().getOwner().getId().equals(shopkeeper.getId())) {
            throw new BadRequestException(
                    "You are not authorized to reject this order"
            );
        }

        // Check the status after taking the row lock so a concurrent acceptance
        // cannot turn a rejection into an unintended cancellation.
        if (order.getStatus() != OrderStatus.PLACED) {
            return Optional.empty();
        }

        return cancelAndRestock(
                order,
                shopkeeper,
                normalizeCancellationReason(reason, "Rejected by shopkeeper")
        );
    }

    @Transactional
    public Optional<Order> cancelOrderByShopkeeper(
            Long orderId,
            String email,
            String reason) {

        User shopkeeper = getShopkeeper(email);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null) {
            return Optional.empty();
        }

        if (order.getShop() == null
                || order.getShop().getOwner() == null
                || !order.getShop().getOwner().getId().equals(shopkeeper.getId())) {
            throw new BadRequestException(
                    "You are not authorized to cancel this order"
            );
        }

        OrderStatus status = order.getStatus();
        boolean cancellable = status == OrderStatus.PLACED
                || status == OrderStatus.ACCEPTED
                || status == OrderStatus.PREPARING
                || status == OrderStatus.READY;

        if (!cancellable) {
            return Optional.empty();
        }

        return cancelAndRestock(
                order,
                shopkeeper,
                normalizeCancellationReason(reason, "Cancelled by shopkeeper")
        );
    }

    private Optional<Order> cancelAndRestock(
            Order order,
            User actor,
            String reason) {

        if (order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY) {
            return Optional.empty();
        }

        for (OrderItem item : order.getItems()) {
            ShopInventory inventory = shopInventoryRepository
                    .findByShopIdAndProductIdForUpdate(
                            order.getShop().getId(),
                            item.getProduct().getId()
                    )
                    .orElseThrow(() ->
                            new BadRequestException(
                                    "Inventory record is missing for product "
                                            + item.getProduct().getName()
                            )
                    );

            int previousQuantity = inventory.getStockQuantity();
            inventory.setStockQuantity(previousQuantity + item.getQuantity());
            inventory.setAvailable(inventory.getStockQuantity() > 0);
            shopInventoryRepository.save(inventory);
            recordInventoryMovement(
                    inventory,
                    InventoryMovementType.CANCELLATION_RESTOCK,
                    previousQuantity,
                    inventory.getStockQuantity(),
                    actor,
                    "Stock restored after cancellation of order " + order.getId()
            );
        }

        OrderStatus previousStatus = order.getStatus();
        order.setDeliveryPartner(null);
        order.setStatus(OrderStatus.CANCELLED);

        recordStatusChange(
                order,
                previousStatus,
                OrderStatus.CANCELLED,
                actor,
                reason
        );

        // A cancelled order cannot remain payable. If it was already paid,
        // mark it for refund reconciliation instead of silently losing the payment.
        paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            } else if (payment.getStatus() == PaymentStatus.PAID) {
                payment.setStatus(PaymentStatus.REFUND_PENDING);
                paymentRepository.save(payment);
            }
        });

        return Optional.of(orderRepository.save(order));
    }

    private String normalizeCancellationReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason.trim();
    }


    private void recordInventoryMovement(
            ShopInventory inventory,
            InventoryMovementType movementType,
            int previousQuantity,
            int newQuantity,
            User actor,
            String note) {

        InventoryMovement movement = new InventoryMovement();
        movement.setInventory(inventory);
        movement.setShopId(inventory.getShop().getId());
        movement.setProductId(inventory.getProduct().getId());
        movement.setProductName(inventory.getProduct().getName());
        movement.setMovementType(movementType);
        movement.setPreviousQuantity(previousQuantity);
        movement.setNewQuantity(newQuantity);
        movement.setQuantityChange(newQuantity - previousQuantity);
        if (actor != null) {
            movement.setPerformedByUserId(actor.getId());
            movement.setPerformedByName(actor.getName());
            movement.setPerformedByRole(actor.getRole().name());
        }
        movement.setNote(note);
        inventoryMovementRepository.save(movement);
    }

    // =========================================================
    // ORDER STATUS HISTORY
    // =========================================================

    private void recordStatusChange(
            Order order,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            User actor,
            String note) {

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());
        history.setNote(note);

        if (actor != null) {
            history.setChangedByUserId(actor.getId());
            history.setChangedByName(actor.getName());
            history.setChangedByEmail(actor.getEmail());
            history.setChangedByRole(actor.getRole().name());
        }

        orderStatusHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderStatusHistory(Long orderId) {
        return orderStatusHistoryRepository
                .findByOrder_IdOrderByChangedAtAscIdAsc(orderId)
                .stream()
                .map(OrderStatusHistoryResponse::from)
                .toList();
    }
}
