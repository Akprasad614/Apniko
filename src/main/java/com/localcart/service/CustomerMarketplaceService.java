package com.localcart.service;

import com.localcart.dto.InventoryResponse;
import com.localcart.dto.PriceComparisonResponse;
import com.localcart.dto.ShopResponse;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ProductRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CustomerMarketplaceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ShopRepository shopRepository;
    private final ShopInventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public CustomerMarketplaceService(
            ShopRepository shopRepository,
            ShopInventoryRepository inventoryRepository,
            ProductRepository productRepository) {

        this.shopRepository = shopRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
    }

    public List<ShopResponse> getActiveShops() {

        return shopRepository.findAll()
                .stream()
                .filter(Shop::isActive)
                .map(this::toShopResponse)
                .toList();
    }

    public ShopResponse getActiveShop(Long shopId) {

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Shop not found"));

        if (!shop.isActive()) {
            throw new ResourceNotFoundException("Shop is not active");
        }

        return toShopResponse(shop);
    }

    public List<InventoryResponse> getShopProducts(Long shopId) {

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Shop not found"));

        if (!shop.isActive()) {
            throw new ResourceNotFoundException("Shop is not active");
        }

        return inventoryRepository.findByShopId(shopId)
                .stream()
                .filter(ShopInventory::isAvailable)
                .filter(inventory -> inventory.getStockQuantity() > 0)
                .sorted((left, right) ->
                        left.getProduct().getName()
                                .compareToIgnoreCase(right.getProduct().getName()))
                .map(this::toInventoryResponse)
                .toList();
    }

    public Page<InventoryResponse> searchProducts(
            String searchTerm,
            String category,
            Long shopId,
            Double minPrice,
            Double maxPrice,
            int page,
            int size,
            String sortBy,
            String direction) {

        validateSearch(page, size, minPrice, maxPrice);

        String normalizedSearch = normalize(searchTerm);
        String normalizedCategory = normalize(category);

        if (shopId != null) {
            Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Shop not found"));

            if (!shop.isActive()) {
                throw new ResourceNotFoundException("Shop is not active");
            }
        }

        Sort sort = buildSort(sortBy, direction);
        Pageable pageable = PageRequest.of(page, size, sort);

        return inventoryRepository.searchMarketplace(
                        normalizedSearch,
                        normalizedCategory,
                        shopId,
                        minPrice,
                        maxPrice,
                        pageable
                )
                .map(this::toInventoryResponse);
    }

    public List<PriceComparisonResponse> compareProductPrices(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }

        return inventoryRepository.findAvailablePricesForProduct(productId)
                .stream()
                .map(this::toPriceComparisonResponse)
                .toList();
    }

    public List<String> getAvailableCategories() {
        return inventoryRepository.findAvailableMarketplaceCategories();
    }

    private void validateSearch(
            int page,
            int size,
            Double minPrice,
            Double maxPrice) {

        if (page < 0) {
            throw new BadRequestException("Page number cannot be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        if (minPrice != null && minPrice < 0) {
            throw new BadRequestException("Minimum price cannot be negative");
        }

        if (maxPrice != null && maxPrice < 0) {
            throw new BadRequestException("Maximum price cannot be negative");
        }

        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BadRequestException(
                    "Minimum price cannot be greater than maximum price");
        }
    }

    private Sort buildSort(String sortBy, String direction) {

        String property = switch (sortBy == null
                ? "name"
                : sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "price" -> "price";
            case "shop" -> "shop.name";
            case "name" -> "product.name";
            default -> throw new BadRequestException(
                    "sortBy must be one of: name, price, shop");
        };

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(
                    direction == null ? "asc" : direction.trim());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "direction must be either asc or desc");
        }

        return Sort.by(sortDirection, property)
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ShopResponse toShopResponse(Shop shop) {

        ShopResponse response = new ShopResponse();
        response.setId(shop.getId());
        response.setName(shop.getName());
        response.setOwnerName(shop.getOwnerName());
        response.setPhone(shop.getPhone());
        response.setAddress(shop.getAddress());
        response.setActive(shop.isActive());
        return response;
    }

    private InventoryResponse toInventoryResponse(ShopInventory inventory) {

        InventoryResponse response = new InventoryResponse();
        response.setId(inventory.getId());
        response.setShopId(inventory.getShop().getId());
        response.setShopName(inventory.getShop().getName());
        response.setProductId(inventory.getProduct().getId());
        response.setProductName(inventory.getProduct().getName());
        response.setCategory(inventory.getProduct().getCategory());
        response.setPrice(inventory.getPrice());
        response.setStockQuantity(inventory.getStockQuantity());
        response.setAvailable(inventory.isAvailable());
        return response;
    }

    private PriceComparisonResponse toPriceComparisonResponse(
            ShopInventory inventory) {

        PriceComparisonResponse response = new PriceComparisonResponse();
        response.setInventoryId(inventory.getId());
        response.setShopId(inventory.getShop().getId());
        response.setShopName(inventory.getShop().getName());
        response.setShopAddress(inventory.getShop().getAddress());
        response.setProductId(inventory.getProduct().getId());
        response.setProductName(inventory.getProduct().getName());
        response.setCategory(inventory.getProduct().getCategory());
        response.setPrice(inventory.getPrice());
        response.setStockQuantity(inventory.getStockQuantity());
        return response;
    }
}
