package com.localcart.repository;

import com.localcart.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items.product"})
    Optional<Cart> findByCustomerIdAndShopId(
            Long customerId,
            Long shopId
    );
}