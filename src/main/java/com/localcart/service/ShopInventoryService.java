package com.localcart.service;

import com.localcart.dto.AddInventoryRequest;
import com.localcart.dto.InventoryMovementResponse;
import com.localcart.dto.InventoryResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
public class ShopInventoryService {

    private static final Logger log = LoggerFactory.getLogger(ShopInventoryService.class);
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final ShopInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductImageStorageService imageStorageService;

    @Autowired
    public ShopInventoryService(
            ShopInventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            ShopRepository shopRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            ProductImageStorageService imageStorageService) {

        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.shopRepository = shopRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
    }

    /*
     * Kept for existing unit-test setup and older direct service construction.
     * Spring uses the @Autowired constructor above and the configured storage path.
     */
    public ShopInventoryService(
            ShopInventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            ShopRepository shopRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this(inventoryRepository, movementRepository, shopRepository, productRepository,
                userRepository, new ProductImageStorageService("uploads/products"));
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

    public InventoryResponse uploadProductImage(
            String email,
            Long shopId,
            Long inventoryId,
            MultipartFile file) {

        User owner = getShopkeeper(email);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        checkOwnership(shop, owner);

        ShopInventory inventory = inventoryRepository
                .findByIdAndShopIdForUpdate(inventoryId, shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found in this shop"));

        String previousImageUrl = inventory.getImageUrl();
        String newImageUrl = imageStorageService.store(file);
        inventory.setImageUrl(newImageUrl);

        ShopInventory saved;
        try {
            saved = inventoryRepository.save(inventory);
        } catch (RuntimeException exception) {
            imageStorageService.delete(newImageUrl);
            throw exception;
        }

        if (previousImageUrl != null && !previousImageUrl.equals(newImageUrl)) {
            try {
                imageStorageService.delete(previousImageUrl);
            } catch (RuntimeException cleanupFailure) {
                // The new image is already saved. A failed cleanup must not undo the upload.
                log.warn("Could not remove the previous image for inventory {}", inventoryId,
                        cleanupFailure);
            }
        }

        return toResponse(saved);
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
        response.setShopLatitude(inventory.getShop().getLatitude());
        response.setShopLongitude(inventory.getShop().getLongitude());
        response.setProductId(inventory.getProduct().getId());
        response.setProductName(inventory.getProduct().getName());
        response.setCategory(inventory.getProduct().getCategory());
        response.setImageUrl(inventory.getImageUrl() != null
                ? inventory.getImageUrl()
                : inventory.getProduct().getImageUrl());
        response.setPrice(inventory.getPrice());
        response.setStockQuantity(inventory.getStockQuantity());
        response.setAvailable(inventory.isAvailable());
        response.setLowStockThreshold(threshold);
        response.setLowStock(inventory.getStockQuantity() <= threshold);
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
