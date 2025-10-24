package ru.javabruse.booking.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class JwtService {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    private final Duration expiration = Duration.ofHours(1);
    
    public String generateToken(Long userId, String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .setClaims(Map.of(
                        "userId", userId,
                        "username", username,
                        "roles", role
                ))
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plus(expiration)))
                .signWith(getSigningKey())
                .compact();
    }
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
