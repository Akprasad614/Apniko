package com.localcart.controller;

import com.localcart.entity.User;
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
    public ResponseEntity<User> getProfile() {

        String email = SecurityUtils.getCurrentUserEmail();

        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findByEmail(email)
                .map(user -> {
                    user.setPassword(null);
                    return ResponseEntity.ok(user);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}