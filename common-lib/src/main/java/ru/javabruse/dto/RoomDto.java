package ru.javabruse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDto {
    private Long id;
    
    @NotNull(message = "Hotel ID is required")
    private Long hotelId;
    
    @NotBlank(message = "Room number is required")
    private String number;
    
    @NotNull(message = "Availability status is required")
    private Boolean available;
    
    private Integer timesBooked;
}
