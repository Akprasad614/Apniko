package com.localcart.repository;

import com.localcart.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    List<Shop> findByOwnerId(Long ownerId);
}