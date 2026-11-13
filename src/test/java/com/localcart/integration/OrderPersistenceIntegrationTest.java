package com.localcart.integration;

import com.localcart.entity.DeliveryMode;
import com.localcart.entity.Order;
import com.localcart.entity.OrderItem;
import com.localcart.entity.OrderStatus;
import com.localcart.entity.Product;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.repository.OrderRepository;
import com.localcart.repository.ProductRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class OrderPersistenceIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void persistsAnOrderAndItsShopCustomerAndLineItems() {
        String suffix = UUID.randomUUID().toString();

        User shopkeeper = new User();
        shopkeeper.setName("Persistence Test Shopkeeper");
        shopkeeper.setEmail("shopkeeper-" + suffix + "@example.test");
        shopkeeper.setPassword("test-password-hash");
        shopkeeper.setRole(Role.SHOPKEEPER);
        shopkeeper.setActive(true);
        shopkeeper = userRepository.saveAndFlush(shopkeeper);

        User customer = new User();
        customer.setName("Persistence Test Customer");
        customer.setEmail("customer-" + suffix + "@example.test");
        customer.setPassword("test-password-hash");
        customer.setRole(Role.CUSTOMER);
        customer.setActive(true);
        customer = userRepository.saveAndFlush(customer);

        Shop shop = new Shop();
        shop.setName("Persistence Test Shop");
        shop.setOwnerName(shopkeeper.getName());
        shop.setPhone("9000000000");
        shop.setAddress("Test address");
        shop.setActive(true);
        shop.setOwner(shopkeeper);
        shop = shopRepository.saveAndFlush(shop);

        Product product = new Product();
        product.setName("Persistence Test Rice " + suffix.substring(0, 8));
        product.setCategory("Test staples");
        product = productRepository.saveAndFlush(product);

        Order order = new Order();
        order.setShop(shop);
        order.setCustomer(customer);
        order.setTotalAmount(120.0);
        order.setStatus(OrderStatus.PLACED);
        order.setDeliveryMode(DeliveryMode.CUSTOMER_PICKUP);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(60.0);
        order.getItems().add(item);

        Order saved = orderRepository.saveAndFlush(order);
        Long orderId = saved.getId();

        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findById(orderId).orElseThrow();

        assertAll(
                () -> assertNotNull(reloaded.getId()),
                () -> assertEquals(OrderStatus.PLACED, reloaded.getStatus()),
                () -> assertEquals(DeliveryMode.CUSTOMER_PICKUP, reloaded.getDeliveryMode()),
                () -> assertEquals(120.0, reloaded.getTotalAmount()),
                () -> assertEquals(shop.getId(), reloaded.getShop().getId()),
                () -> assertEquals(customer.getId(), reloaded.getCustomer().getId()),
                () -> assertEquals(1, reloaded.getItems().size()),
                () -> assertEquals(product.getId(), reloaded.getItems().get(0).getProduct().getId()),
                () -> assertEquals(2, reloaded.getItems().get(0).getQuantity()),
                () -> assertEquals(60.0, reloaded.getItems().get(0).getPrice())
        );
    }
}
