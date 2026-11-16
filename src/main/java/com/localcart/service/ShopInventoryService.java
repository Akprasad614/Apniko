package com.localcart.service;

import com.localcart.dto.AddInventoryRequest;
import com.localcart.dto.InventoryMovementResponse;
import com.localcart.dto.InventoryResponse;
import com.localcart.dto.ProductResponse;
import com.localcart.dto.RestockInventoryRequest;
import com.localcart.dto.UpdateInventoryRequest;
import com.localcart.dto.UpdateLowStockThresholdRequest;
import com.localcart.entity.InventoryMovement;
import com.localcart.entity.InventoryMovementType;
import com.localcart.entity.Product;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.ShopInventory;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.InventoryMovementRepository;
import com.localcart.repository.ProductRepository;
import com.localcart.repository.ShopInventoryRepository;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ShopInventoryService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final ShopInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ShopInventoryService(
            ShopInventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            ShopRepository shopRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {

        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.shopRepository = shopRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public InventoryResponse addInventory(
            String email,
            Long shopId,
            AddInventoryRequest request) {

        User owner = getShopkeeper(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        checkOwnership(shop, owner);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (request.getPrice() < 0 || request.getStockQuantity() < 0) {
            throw new BadRequestException("Price and stock quantity cannot be negative");
        }

        if (inventoryRepository.findByShopIdAndProductId(shopId, request.getProductId()).isPresent()) {
            throw new BadRequestException("Product already exists in this shop inventory");
        }

        ShopInventory inventory = new ShopInventory();
        inventory.setShop(shop);
        inventory.setProduct(product);
        inventory.setPrice(request.getPrice());
        inventory.setStockQuantity(request.getStockQuantity());
        inventory.setAvailable(request.getStockQuantity() > 0);
        inventory.setLowStockThreshold(DEFAULT_LOW_STOCK_THRESHOLD);

        ShopInventory saved = inventoryRepository.save(inventory);
        recordMovement(saved, InventoryMovementType.INITIAL_STOCK,
                0, saved.getStockQuantity(), owner,
                "Initial inventory entry");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getMyInventory(String email, Long shopId) {
        User owner = getShopkeeper(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        checkOwnership(shop, owner);

        return inventoryRepository.findByShopId(shopId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchAddableProducts(
            String email, Long shopId, String searchTerm, int page, int size) {
        User owner = getShopkeeper(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        checkOwnership(shop, owner);

        if (page < 0) {
            throw new BadRequestException("Page number cannot be negative");
        }
        if (size < 1 || size > 48) {
            throw new BadRequestException("Catalogue page size must be between 1 and 48");
        }

        String term = searchTerm == null || searchTerm.isBlank()
                ? null : searchTerm.trim();
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
        return productRepository.findAddableProductsForShop(
                        shopId, term, ProductCatalogueReviewStatus.ACTIVE, pageable)
                .map(ProductResponse::from);
    }

    public InventoryResponse updateInventory(
            String email,
            Long inventoryId,
            UpdateInventoryRequest request) {

        User owner = getShopkeeper(email);
        ShopInventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        checkOwnership(inventory.getShop(), owner);

        if (request.getPrice() < 0 || request.getStockQuantity() < 0) {
            throw new BadRequestException("Price and stock quantity cannot be negative");
        }

        int previousQuantity = inventory.getStockQuantity();
        inventory.setPrice(request.getPrice());
        inventory.setStockQuantity(request.getStockQuantity());
        inventory.setAvailable(request.getStockQuantity() > 0 && request.isAvailable());

        ShopInventory saved = inventoryRepository.save(inventory);
        if (previousQuantity != saved.getStockQuantity()) {
            recordMovement(saved, InventoryMovementType.ADJUSTMENT,
                    previousQuantity, saved.getStockQuantity(), owner,
                    "Stock quantity manually adjusted");
        }
        return toResponse(saved);
    }

    public InventoryResponse restock(
            String email,
            Long shopId,
            Long inventoryId,
            RestockInventoryRequest request) {

        User owner = getShopkeeper(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        checkOwnership(shop, owner);

        ShopInventory inventory = inventoryRepository
                .findByIdAndShopIdForUpdate(inventoryId, shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found in this shop"));

        int previousQuantity = inventory.getStockQuantity();
        int newQuantity;
        try {
            newQuantity = Math.addExact(previousQuantity, request.getQuantity());
        } catch (ArithmeticException exception) {
            throw new BadRequestException("Restock quantity exceeds the supported stock limit");
        }

        inventory.setStockQuantity(newQuantity);
        inventory.setAvailable(newQuantity > 0);
        ShopInventory saved = inventoryRepository.save(inventory);

        String note = request.getNote() == null || request.getNote().isBlank()
                ? "Stock replenished"
                : request.getNote().trim();
        recordMovement(saved, InventoryMovementType.RESTOCK,
                previousQuantity, newQuantity, owner, note);
        return toResponse(saved);
    }

    public InventoryResponse updateLowStockThreshold(
            String email,
            Long shopId,
            Long inventoryId,
            UpdateLowStockThresholdRequest request) {

        User owner = getShopkeeper(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        checkOwnership(shop, owner);

        ShopInventory inventory = inventoryRepository
                .findByIdAndShopIdForUpdate(inventoryId, shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found in this shop"));

        inventory.setLowStockThreshold(request.getLowStockThreshold());
        return toResponse(inventoryRepository.save(inventory));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getLowStockItems(String email, Long shopId) {
        User owner = getShopkeeper(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        checkOwnership(shop, owner);

        return inventoryRepository.findByShopId(shopId).stream()
                .filter(item -> item.getStockQuantity() <= effectiveThreshold(item))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> getMovementHistory(
            String email,
            Long shopId,
            Long inventoryId) {

        User owner = getShopkeeper(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        checkOwnership(shop, owner);

        ShopInventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found in this shop"));
        if (!inventory.getShop().getId().equals(shopId)) {
            throw new ResourceNotFoundException("Inventory item not found in this shop");
        }

        return movementRepository
                .findByInventoryIdAndShopIdOrderByCreatedAtDescIdDesc(inventoryId, shopId)
                .stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

    private User getShopkeeper(String email) {
        if (email == null) {
            throw new BadRequestException("User is not authenticated");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.SHOPKEEPER) {
            throw new BadRequestException("Only a shopkeeper can perform this action");
        }
        if (!user.isActive()) {
            throw new BadRequestException("Shopkeeper account is inactive");
        }
        return user;
    }

    private void checkOwnership(Shop shop, User owner) {
        if (shop.getOwner() == null || !shop.getOwner().getId().equals(owner.getId())) {
            throw new BadRequestException("You do not own this shop");
        }
    }

    private int effectiveThreshold(ShopInventory inventory) {
        return inventory.getLowStockThreshold() == null
                ? DEFAULT_LOW_STOCK_THRESHOLD
                : inventory.getLowStockThreshold();
    }

    private InventoryResponse toResponse(ShopInventory inventory) {
        int threshold = effectiveThreshold(inventory);
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
        response.setLowStockThreshold(threshold);
        response.setLowStock(inventory.getStockQuantity() <= threshold);
        var photo = inventory.getProduct().getPhoto();
        if (photo != null && photo.getApprovedImageUrl() != null) {
            response.setImageUrl(photo.getApprovedImageUrl());
            response.setImageSourceName(photo.getSourceName());
            response.setImageSourceUrl(photo.getSourceUrl());
            response.setImageLicense(photo.getLicense());
            response.setImageAttribution(photo.getAttribution());
        }
        return response;
    }

    private void recordMovement(
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
        movementRepository.save(movement);
    }
}
