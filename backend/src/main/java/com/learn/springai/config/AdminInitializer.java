package com.learn.springai.config;

import com.learn.springai.model.User;
import com.learn.springai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPasswordHash;

    @Override
    public void run(String... args) {
        try {
            if (!userRepository.existsByEmail(adminEmail)) {
                log.info("Seeding default system administrator user: {}", adminEmail);
                User admin = User.builder()
                        .name(adminUsername)
                        .email(adminEmail)
                        .password(adminPasswordHash)
                        .role("ADMIN")
                        .blocked(false)
                        .deleted(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(admin);
                log.info("Default system administrator seeded successfully!");
            }
        } catch (Exception e) {
            log.error("Failed to seed default system administrator user: {}", e.getMessage(), e);
        }
    }
}
