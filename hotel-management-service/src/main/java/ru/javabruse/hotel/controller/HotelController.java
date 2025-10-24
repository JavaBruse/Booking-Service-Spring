package ru.javabruse.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.javabruse.dto.HotelDto;
import ru.javabruse.dto.RoomAvailabilityRequest;
import ru.javabruse.dto.RoomDto;
import ru.javabruse.service.HotelService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Hotel Management", description = "Hotel and room management operations")
public class HotelController {
    
    private final HotelService hotelService;
    
    @GetMapping("/hotels")
    @Operation(summary = "Get all hotels", description = "Retrieve list of all hotels")
    public ResponseEntity<List<HotelDto>> getAllHotels() {
        List<HotelDto> hotels = hotelService.getAllHotels();
        return ResponseEntity.ok(hotels);
    }
    
    @PostMapping("/hotels")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create hotel", description = "Create a new hotel (ADMIN only)")
    public ResponseEntity<HotelDto> createHotel(@Valid @RequestBody HotelDto hotelDto) {
        HotelDto createdHotel = hotelService.createHotel(hotelDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdHotel);
    }
    
    @GetMapping("/rooms")
    @Operation(summary = "Get all available rooms", description = "Retrieve list of all available rooms")
    public ResponseEntity<List<RoomDto>> getAllAvailableRooms() {
        List<RoomDto> rooms = hotelService.getAllAvailableRooms();
        return ResponseEntity.ok(rooms);
    }
    
    @GetMapping("/rooms/recommend")
    @Operation(summary = "Get recommended rooms", description = "Get rooms sorted by times booked (ascending)")
    public ResponseEntity<List<RoomDto>> getRecommendedRooms() {
        List<RoomDto> rooms = hotelService.getRecommendedRooms();
        return ResponseEntity.ok(rooms);
    }
    
    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create room", description = "Create a new room (ADMIN only)")
    public ResponseEntity<RoomDto> createRoom(@Valid @RequestBody RoomDto roomDto) {
        RoomDto createdRoom = hotelService.createRoom(roomDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRoom);
    }

    @PostMapping("/rooms/{id}/confirm-availability")
    @Operation(summary = "Confirm room availability", description = "Confirm room availability for booking (INTERNAL)")
    public ResponseEntity<Boolean> confirmRoomAvailability(
            @PathVariable("id") Long id,
            @Valid @RequestBody RoomAvailabilityRequest request) {
        request.setRoomId(id);
        boolean confirmed = hotelService.confirmRoomAvailability(request);
        return ResponseEntity.ok(confirmed);
    }

    @PostMapping("/rooms/{id}/release")
    @Operation(summary = "Release room", description = "Release room block (INTERNAL)")
    public ResponseEntity<Void> releaseRoom(
            @PathVariable("id") Long id,
            @RequestParam("requestId") String requestId) {
        hotelService.releaseRoom(requestId);
        return ResponseEntity.ok().build();
    }
}
