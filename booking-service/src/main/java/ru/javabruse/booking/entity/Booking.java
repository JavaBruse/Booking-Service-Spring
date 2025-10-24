package ru.javabruse.booking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_booking_user_id", columnList = "user_id"),
    @Index(name = "idx_booking_room_id", columnList = "room_id"),
    @Index(name = "idx_booking_status", columnList = "status"),
    @Index(name = "idx_booking_request_id", columnList = "requestId"),
    @Index(name = "idx_booking_dates", columnList = "start_date, end_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private Long roomId;
    
    @Column(nullable = false)
    private LocalDateTime startDate;
    
    @Column(nullable = false)
    private LocalDateTime endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column
    private String requestId;
    
    public enum BookingStatus {
        PENDING, CONFIRMED, CANCELLED
    }
}
