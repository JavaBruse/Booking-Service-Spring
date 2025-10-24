package ru.javabruse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelDto {
    private Long id;
    
    @NotBlank(message = "Hotel name is required")
    private String name;
    
    @NotBlank(message = "Hotel address is required")
    private String address;
}
