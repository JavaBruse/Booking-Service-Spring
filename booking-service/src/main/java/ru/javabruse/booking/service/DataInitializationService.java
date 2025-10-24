package ru.javabruse.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javabruse.booking.entity.User;
import ru.javabruse.booking.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitializationService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Starting data initialization...");
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role(User.Role.ADMIN)
                    .build();
            userRepository.save(admin);
            log.info("Created admin user: admin/admin123");
        }
        createTestUser("user1", "password123");
        createTestUser("user2", "password123");
        createTestUser("user3", "password123");

        log.info("Data initialization completed");
    }

    private void createTestUser(String username, String password) {
        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .role(User.Role.USER)
                    .build();
            userRepository.save(user);
            log.info("Created test user: {}/{}", username, password);
        }
    }
}
