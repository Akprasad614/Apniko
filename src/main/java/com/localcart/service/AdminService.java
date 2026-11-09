package com.localcart.service;

import com.localcart.dto.AdminShopResponse;
import com.localcart.dto.AssignShopOwnerRequest;
import com.localcart.dto.CreateManagedUserRequest;
import com.localcart.dto.UpdateActiveStatusRequest;
import com.localcart.dto.UserResponse;
import com.localcart.entity.Role;
import com.localcart.entity.Shop;
import com.localcart.entity.User;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ShopRepository;
import com.localcart.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        ShopRepository shopRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse createShopkeeper(CreateManagedUserRequest request) {
        return createManagedUser(request, Role.SHOPKEEPER);
    }

    public UserResponse createDeliveryPartner(CreateManagedUserRequest request) {
        return createManagedUser(request, Role.DELIVERY_PARTNER);
    }

    private UserResponse createManagedUser(CreateManagedUserRequest request, Role role) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setActive(true);

        return toUserResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(Role role) {
        List<User> users = role == null
                ? userRepository.findAll()
                : userRepository.findByRole(role);
        return users.stream().map(this::toUserResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + userId
                ));
        return toUserResponse(user);
    }

    public UserResponse updateUserActiveStatus(Long userId,
                                               UpdateActiveStatusRequest request,
                                               String currentAdminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + userId
                ));

        if (user.getEmail().equalsIgnoreCase(currentAdminEmail)
                && Boolean.FALSE.equals(request.getActive())) {
            throw new BadRequestException("You cannot deactivate your own admin account");
        }

        if (user.getRole() == Role.ADMIN && Boolean.FALSE.equals(request.getActive())) {
            long activeAdmins = userRepository.countByRoleAndActiveTrue(Role.ADMIN);
            if (activeAdmins <= 1) {
                throw new BadRequestException("The last active admin account cannot be deactivated");
            }
        }

        user.setActive(request.getActive());
        return toUserResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<AdminShopResponse> getShops() {
        return shopRepository.findAll().stream()
                .map(this::toAdminShopResponse)
                .toList();
    }

    public AdminShopResponse assignShopOwner(Long shopId,
                                             AssignShopOwnerRequest request) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shop not found with id " + shopId
                ));

        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shopkeeper not found with id " + request.getOwnerId()
                ));

        if (owner.getRole() != Role.SHOPKEEPER) {
            throw new BadRequestException("The assigned owner must have SHOPKEEPER role");
        }
        if (!owner.isActive()) {
            throw new BadRequestException("An inactive shopkeeper cannot own a shop");
        }

        shop.setOwner(owner);
        shop.setOwnerName(owner.getName());
        return toAdminShopResponse(shopRepository.save(shop));
    }

    public AdminShopResponse updateShopActiveStatus(Long shopId,
                                                    UpdateActiveStatusRequest request) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shop not found with id " + shopId
                ));
        shop.setActive(request.getActive());
        return toAdminShopResponse(shopRepository.save(shop));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.isActive()
        );
    }

    private AdminShopResponse toAdminShopResponse(Shop shop) {
        User owner = shop.getOwner();
        return new AdminShopResponse(
                shop.getId(),
                shop.getName(),
                owner == null ? null : owner.getId(),
                owner == null ? shop.getOwnerName() : owner.getName(),
                owner == null ? null : owner.getEmail(),
                shop.getPhone(),
                shop.getAddress(),
                shop.isActive()
        );
    }
}
