package ru.javabruse.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.javabruse.dto.RoomAvailabilityRequest;
import ru.javabruse.dto.RoomDto;

import java.util.List;

@FeignClient(name = "hotel-management-service")
public interface HotelServiceClient {

    @PostMapping("/api/rooms/{id}/confirm-availability")
    Boolean confirmRoomAvailability(@PathVariable("id") Long id, @RequestBody RoomAvailabilityRequest request);

    @PostMapping("/api/rooms/{id}/release")
    void releaseRoom(@PathVariable("id") Long id, @RequestParam("requestId") String requestId);

    @GetMapping("/api/rooms/recommend")
    List<RoomDto> getRecommendedRooms();
}
