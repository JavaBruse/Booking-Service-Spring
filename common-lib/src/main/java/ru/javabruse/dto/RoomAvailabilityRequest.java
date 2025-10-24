package ru.javabruse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomAvailabilityRequest {
    @NotNull(message = "Room ID is required")
    private Long roomId;
    
    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;
    
    @NotNull(message = "End date is required")
    private LocalDateTime endDate;
    
    @NotNull(message = "Booking ID is required")
    private String bookingId;
    
    @NotNull(message = "Request ID is required")
    private String requestId;
}
