package ru.javabruse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javabruse.dto.HotelDto;
import ru.javabruse.dto.RoomAvailabilityRequest;
import ru.javabruse.dto.RoomDto;
import ru.javabruse.entity.Hotel;
import ru.javabruse.entity.Room;
import ru.javabruse.entity.RoomAvailability;
import ru.javabruse.repository.HotelRepository;
import ru.javabruse.repository.RoomAvailabilityRepository;
import ru.javabruse.repository.RoomRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelService {
    
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomAvailabilityRepository roomAvailabilityRepository;
    
    @Transactional(readOnly = true)
    public List<HotelDto> getAllHotels() {
        return hotelRepository.findAll().stream()
                .map(this::convertToDto)
                .toList();
    }
    
    @Transactional
    public HotelDto createHotel(HotelDto hotelDto) {
        Hotel hotel = Hotel.builder()
                .name(hotelDto.getName())
                .address(hotelDto.getAddress())
                .build();
        
        Hotel savedHotel = hotelRepository.save(hotel);
        return convertToDto(savedHotel);
    }
    
    @Transactional(readOnly = true)
    public List<RoomDto> getAllAvailableRooms() {
        return roomRepository.findAvailableRoomsOrderedByTimesBooked().stream()
                .map(this::convertToDto)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public List<RoomDto> getRecommendedRooms() {
        return roomRepository.findAvailableRoomsOrderedByTimesBooked().stream()
                .map(this::convertToDto)
                .toList();
    }
    
    @Transactional
    public RoomDto createRoom(RoomDto roomDto) {
        Hotel hotel = hotelRepository.findById(roomDto.getHotelId())
                .orElseThrow(() -> new RuntimeException("Hotel not found"));
        
        Room room = Room.builder()
                .hotel(hotel)
                .number(roomDto.getNumber())
                .available(roomDto.getAvailable())
                .timesBooked(0)
                .build();
        
        Room savedRoom = roomRepository.save(room);
        return convertToDto(savedRoom);
    }
    
    @Transactional
    public boolean confirmRoomAvailability(RoomAvailabilityRequest request) {
        log.info("Confirming room availability for room {} with requestId {}", 
                request.getRoomId(), request.getRequestId());
        
        if (roomAvailabilityRepository.findByRequestId(request.getRequestId()).isPresent()) {
            log.info("Request {} already processed", request.getRequestId());
            return true;
        }
        
        List<RoomAvailability> conflicts = roomAvailabilityRepository.findConflictingBlocks(
                request.getRoomId(), request.getStartDate(), request.getEndDate());
        
        if (!conflicts.isEmpty()) {
            log.warn("Room {} has conflicts for period {} - {}", 
                    request.getRoomId(), request.getStartDate(), request.getEndDate());
            return false;
        }
        
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        RoomAvailability availability = RoomAvailability.builder()
                .room(room)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isBlocked(true)
                .bookingId(request.getBookingId())
                .requestId(request.getRequestId())
                .build();
        
        roomAvailabilityRepository.save(availability);
        log.info("Room availability confirmed for room {} with requestId {}", 
                request.getRoomId(), request.getRequestId());
        
        return true;
    }
    
    @Transactional
    public void releaseRoom(String requestId) {
        log.info("Releasing room for requestId {}", requestId);
        
        roomAvailabilityRepository.deleteByRequestId(requestId);
        log.info("Room released for requestId {}", requestId);
    }
    
    @Transactional
    public void incrementTimesBooked(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        room.setTimesBooked(room.getTimesBooked() + 1);
        roomRepository.save(room);
        
        log.info("Incremented times booked for room {} to {}", 
                roomId, room.getTimesBooked());
    }
    
    private HotelDto convertToDto(Hotel hotel) {
        return HotelDto.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .address(hotel.getAddress())
                .build();
    }
    
    private RoomDto convertToDto(Room room) {
        return RoomDto.builder()
                .id(room.getId())
                .hotelId(room.getHotel().getId())
                .number(room.getNumber())
                .available(room.getAvailable())
                .timesBooked(room.getTimesBooked())
                .build();
    }
}
