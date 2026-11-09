package com.localcart.config;

import com.localcart.entity.Role;
import com.localcart.entity.User;
import com.localcart.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin only when explicit bootstrap environment variables
 * are supplied. No public HTTP endpoint can create an ADMIN account.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_BOOTSTRAP_EMAIL:}") String adminEmail,
            @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean emailProvided = adminEmail != null && !adminEmail.isBlank();
        boolean passwordProvided = adminPassword != null && !adminPassword.isBlank();

        if (!emailProvided && !passwordProvided) {
            return; // Normal startup after the first admin has been created.
        }
        if (!emailProvided || !passwordProvided) {
            throw new IllegalStateException(
                    "Set both ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD, or unset both"
            );
        }
        if (adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_PASSWORD must contain at least 12 characters"
            );
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();
        var existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                throw new IllegalStateException(
                        "The bootstrap email already belongs to a non-admin account"
                );
            }
            return;
        }

        User admin = new User();
        admin.setName("LocalCart Administrator");
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
    }
}
