package com.localcart.service;

import com.localcart.dto.InventoryResponse;
import com.localcart.dto.PriceComparisonResponse;
import com.localcart.dto.CustomerMarketplaceShopResponse;
import com.localcart.entity.Shop;
import com.localcart.entity.ProductCatalogueReviewStatus;
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
import java.util.Comparator;

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

    public List<CustomerMarketplaceShopResponse> getActiveShops() {

        return shopRepository.findAll()
                .stream()
                .filter(Shop::isActive)
                .map(this::toCustomerShopResponse)
                .toList();
    }

    /**
     * The customer's coordinates are used for this one search only and are never persisted.
     */
    public List<CustomerMarketplaceShopResponse> getNearbyShops(
            double latitude, double longitude, double radiusKm) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new BadRequestException("Enter valid latitude and longitude coordinates");
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > 50) {
            throw new BadRequestException("Search radius must be greater than 0 and no more than 50 km");
        }

        return shopRepository.findAll().stream()
                .filter(Shop::isActive)
                .filter(shop -> shop.getLatitude() != null && shop.getLongitude() != null)
                .map(shop -> new NearbyShop(shop,
                        distanceKm(latitude, longitude, shop.getLatitude(), shop.getLongitude())))
                .filter(item -> item.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyShop::distanceKm)
                        .thenComparing(item -> item.shop().getName(), String.CASE_INSENSITIVE_ORDER))
                .limit(100)
                .map(item -> toCustomerShopResponse(item.shop(), item.distanceKm()))
                .toList();
    }

    private record NearbyShop(Shop shop, double distanceKm) { }

    private double distanceKm(double latitude1, double longitude1,
                              double latitude2, double longitude2) {
        final double earthRadiusKm = 6371.0088;
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double latDelta = Math.toRadians(latitude2 - latitude1);
        double lonDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
        a = Math.min(1.0, Math.max(0.0, a));
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public CustomerMarketplaceShopResponse getActiveShop(Long shopId) {

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Shop not found"));

        if (!shop.isActive()) {
            throw new ResourceNotFoundException("Shop is not active");
        }

        return toCustomerShopResponse(shop);
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
                .filter(inventory -> inventory.getProduct().isVisibleToCustomers())
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
                        ProductCatalogueReviewStatus.ACTIVE,
                        pageable
                )
                .map(this::toInventoryResponse);
    }

    public List<PriceComparisonResponse> compareProductPrices(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }

        return inventoryRepository.findAvailablePricesForProduct(
                        productId, ProductCatalogueReviewStatus.ACTIVE)
                .stream()
                .map(this::toPriceComparisonResponse)
                .toList();
    }

    public List<String> getAvailableCategories() {
        return inventoryRepository.findAvailableMarketplaceCategories(
                ProductCatalogueReviewStatus.ACTIVE);
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

    private CustomerMarketplaceShopResponse toCustomerShopResponse(Shop shop) {
        return toCustomerShopResponse(shop, null);
    }

    private CustomerMarketplaceShopResponse toCustomerShopResponse(Shop shop, Double distanceKm) {
        return new CustomerMarketplaceShopResponse(
                shop.getId(),
                shop.getName(),
                shop.getAddress(),
                shop.isActive(),
                shop.getDeliveryMode(),
                shop.getLatitude(),
                shop.getLongitude(),
                distanceKm == null ? null : Math.round(distanceKm * 10.0) / 10.0
        );
    }

    private InventoryResponse toInventoryResponse(ShopInventory inventory) {

        InventoryResponse response = new InventoryResponse();
        response.setId(inventory.getId());
        response.setShopId(inventory.getShop().getId());
        response.setShopName(inventory.getShop().getName());
        response.setProductId(inventory.getProduct().getId());
        response.setProductName(inventory.getProduct().getName());
        response.setCategory(inventory.getProduct().getCategory());
        response.setSubcategory(inventory.getProduct().getSubcategory());
        response.setBrand(inventory.getProduct().getBrand());
        response.setBarcode(inventory.getProduct().getBarcode());
        response.setPackSize(inventory.getProduct().getPackSize());
        response.setMrp(inventory.getProduct().getMrp());
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
