package ru.javabruse.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.javabruse.booking.dto.AuthResponse;
import ru.javabruse.booking.dto.BookingDto;
import ru.javabruse.booking.dto.BookingRequest;
import ru.javabruse.booking.dto.UserLoginRequest;
import ru.javabruse.booking.dto.UserRegistrationRequest;
import ru.javabruse.booking.entity.User;
import ru.javabruse.booking.service.AuthService;
import ru.javabruse.booking.service.BookingService;
import ru.javabruse.booking.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Booking Service", description = "Booking and user management operations")
public class BookingController {

    private final AuthService authService;
    private final BookingService bookingService;
    private final UserService userService;

    @PostMapping("/user/register")
    @Operation(summary = "Register user", description = "Register a new user and generate JWT token")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/user/auth")
    @Operation(summary = "Authenticate user", description = "Authenticate user and generate JWT token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody UserLoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/booking")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create booking", description = "Create a new booking (USER only)")
    public ResponseEntity<BookingDto> createBooking(@Valid @RequestBody BookingRequest request,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        BookingDto booking = bookingService.createBooking(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get user bookings", description = "Get booking history for current user with pagination")
    public ResponseEntity<Page<BookingDto>> getUserBookings(
            Authentication authentication,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Long userId = getUserIdFromAuthentication(authentication);
        Pageable pageable = PageRequest.of(page, size);
        Page<BookingDto> bookings = bookingService.getUserBookings(userId, pageable);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/booking/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get booking by ID", description = "Get specific booking by ID")
    public ResponseEntity<BookingDto> getBooking(@PathVariable("id") Long id, Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        BookingDto booking = bookingService.getBooking(id, userId);
        return ResponseEntity.ok(booking);
    }

    @DeleteMapping("/booking/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cancel booking", description = "Cancel a booking")
    public ResponseEntity<Void> cancelBooking(@PathVariable("id") Long id, Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        bookingService.cancelBooking(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users", description = "Get list of all users (ADMIN only)")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/user")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create user", description = "Create a new user (ADMIN only)")
    public ResponseEntity<User> createUser(@Valid @RequestBody UserRegistrationRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PatchMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user", description = "Update user information (ADMIN only)")
    public ResponseEntity<User> updateUser(@PathVariable Long id,
            @Valid @RequestBody UserRegistrationRequest request) {
        User user = userService.updateUser(id, request);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user", description = "Delete user (ADMIN only)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("User not authenticated");
        }
        return Long.valueOf(authentication.getName());
    }
}
