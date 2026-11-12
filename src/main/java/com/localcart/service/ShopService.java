package com.localcart.service;

import com.localcart.dto.CreateShopRequest;
import com.localcart.dto.ShopResponse;
import com.localcart.dto.UpdateShopRequest;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public ShopService(
            ShopRepository shopRepository,
            UserRepository userRepository) {

        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    public ShopResponse createShop(
            String email,
            CreateShopRequest request) {

        User owner = getShopkeeper(email);

        Shop shop = new Shop();

        shop.setName(request.getName());
        shop.setOwnerName(owner.getName());
        shop.setPhone(request.getPhone());
        shop.setAddress(request.getAddress());
        validateCoordinates(request.getLatitude(), request.getLongitude());
        shop.setLatitude(request.getLatitude());
        shop.setLongitude(request.getLongitude());
        shop.setActive(true);
        shop.setOwner(owner);

        return toResponse(
                shopRepository.save(shop)
        );
    }

    public List<ShopResponse> getAllShops() {

        return shopRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ShopResponse getShopById(Long id) {

        Shop shop = shopRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Shop not found"
                        ));

        return toResponse(shop);
    }

    public List<ShopResponse> getShopsByOwner(Long ownerId) {

        if (!userRepository.existsById(ownerId)) {
            throw new ResourceNotFoundException(
                    "Owner not found"
            );
        }

        return shopRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ShopResponse updateShop(
            Long shopId,
            String email,
            UpdateShopRequest request) {

        User owner = getShopkeeper(email);

        Shop shop = getShop(shopId);

        checkOwnership(shop, owner);

        shop.setName(request.getName());
        shop.setPhone(request.getPhone());
        shop.setAddress(request.getAddress());
        if (request.getLatitude() != null || request.getLongitude() != null) {
            validateCoordinates(request.getLatitude(), request.getLongitude());
            shop.setLatitude(request.getLatitude());
            shop.setLongitude(request.getLongitude());
        }

        return toResponse(
                shopRepository.save(shop)
        );
    }

    public ShopResponse deactivateShop(
            Long shopId,
            String email) {

        User owner = getShopkeeper(email);

        Shop shop = getShop(shopId);

        checkOwnership(shop, owner);

        shop.setActive(false);

        return toResponse(
                shopRepository.save(shop)
        );
    }

    private User getShopkeeper(String email) {

        if (email == null) {
            throw new BadRequestException(
                    "User is not authenticated"
            );
        }

        User owner = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        ));

        if (owner.getRole() != Role.SHOPKEEPER) {
            throw new BadRequestException(
                    "Only a shopkeeper can perform this action"
            );
        }

        if (!owner.isActive()) {
            throw new BadRequestException(
                    "Shopkeeper account is inactive"
            );
        }

        return owner;
    }

    private Shop getShop(Long id) {

        return shopRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Shop not found"
                        ));
    }

    private void checkOwnership(
            Shop shop,
            User owner) {

        if (shop.getOwner() == null ||
                !shop.getOwner().getId().equals(owner.getId())) {

            throw new BadRequestException(
                    "You do not own this shop"
            );
        }
    }


    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new BadRequestException(
                    "Both latitude and longitude must be provided together");
        }
        if (latitude == null) {
            return;
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new BadRequestException("Latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new BadRequestException("Longitude must be between -180 and 180");
        }
    }

    private ShopResponse toResponse(Shop shop) {
        return new ShopResponse(
                shop.getId(),
                shop.getName(),
                shop.getOwnerName(),
                shop.getPhone(),
                shop.getAddress(),
                shop.isActive(),
                shop.getLatitude(),
                shop.getLongitude()
        );
    }
}