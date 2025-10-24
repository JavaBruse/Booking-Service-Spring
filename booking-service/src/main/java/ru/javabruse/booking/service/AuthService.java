package ru.javabruse.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javabruse.booking.dto.AuthResponse;
import ru.javabruse.booking.dto.UserLoginRequest;
import ru.javabruse.booking.dto.UserRegistrationRequest;
import ru.javabruse.booking.entity.User;
import ru.javabruse.booking.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    
    @Transactional
    public AuthResponse register(UserRegistrationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.USER)
                .build();
        
        User savedUser = userRepository.save(user);
        
        String token = jwtService.generateToken(savedUser.getId(), savedUser.getUsername(), savedUser.getRole().name());
        
        return AuthResponse.builder()
                .token(token)
                .username(savedUser.getUsername())
                .role(savedUser.getRole().name())
                .build();
    }
    
    @Transactional(readOnly = true)
    public AuthResponse login(UserLoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }
}
