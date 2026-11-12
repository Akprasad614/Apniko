package com.localcart.controller;

import com.localcart.dto.UserResponse;
import com.localcart.repository.UserRepository;
import com.localcart.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<UserResponse> getProfile() {

        String email = SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findByEmail(email)
                .map(user -> ResponseEntity.ok(new UserResponse(
                        user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
