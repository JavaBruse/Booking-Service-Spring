package ru.javabruse.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javabruse.booking.client.HotelServiceClient;
import ru.javabruse.booking.dto.BookingDto;
import ru.javabruse.booking.dto.BookingRequest;
import ru.javabruse.booking.entity.Booking;
import ru.javabruse.booking.entity.User;
import ru.javabruse.booking.repository.BookingRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.javabruse.booking.repository.UserRepository;
import ru.javabruse.dto.RoomAvailabilityRequest;
import ru.javabruse.dto.RoomDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final HotelServiceClient hotelServiceClient;
    private final UserRepository userRepository;

    @Transactional
    public BookingDto createBooking(BookingRequest request, Long userId) {
        User user = userRepository.getReferenceById(userId);

        log.info("Creating booking for user {} with request: {}", user.getId(), request);

        String requestId = UUID.randomUUID().toString();

        Long roomId = request.getRoomId();
        if (Boolean.TRUE.equals(request.getAutoSelect())) {
            log.info("Auto-selecting room for user {}", userId);
            roomId = selectRecommendedRoom(request.getStartDate(), request.getEndDate());
            if (roomId == null) {
                throw new RuntimeException("No available rooms found for the requested period");
            }
            log.info("Selected room {} for auto-booking", roomId);
        }

        Booking booking = Booking.builder()
                .user(user)
                .roomId(roomId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(Booking.BookingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Booking {} created with status PENDING", savedBooking.getId());

        try {
            RoomAvailabilityRequest availabilityRequest = RoomAvailabilityRequest.builder()
                    .roomId(roomId)
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .bookingId(savedBooking.getId().toString())
                    .requestId(requestId)
                    .build();

            try {
                boolean confirmed = confirmRoomAvailabilityWithRetry(roomId, availabilityRequest);

                if (confirmed) {
                    savedBooking.setStatus(Booking.BookingStatus.CONFIRMED);
                    bookingRepository.save(savedBooking);
                    log.info("Booking {} confirmed successfully", savedBooking.getId());
                } else {
                    savedBooking.setStatus(Booking.BookingStatus.CANCELLED);
                    bookingRepository.save(savedBooking);
                    log.warn("Booking {} cancelled due to room unavailability", savedBooking.getId());
                }
            } catch (Exception retryException) {
                // Retry exhausted, cancel booking
                savedBooking.setStatus(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(savedBooking);
                log.warn("Booking {} cancelled after retry exhaustion: {}", savedBooking.getId(),
                        retryException.getMessage());
            }

        } catch (Exception e) {
            log.error("Error confirming booking {}: {}", savedBooking.getId(), e.getMessage());

            savedBooking.setStatus(Booking.BookingStatus.CANCELLED);
            bookingRepository.save(savedBooking);

            try {
                hotelServiceClient.releaseRoom(roomId, requestId);
            } catch (Exception releaseException) {
                log.error("Error releasing room for booking {}: {}", savedBooking.getId(),
                        releaseException.getMessage());
            }
        }

        return convertToDto(savedBooking);
    }

    private boolean confirmRoomAvailabilityWithRetry(Long roomId, RoomAvailabilityRequest request) {
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .retryOn(RuntimeException.class)
                .build();

        return retryTemplate.execute(context -> {
            log.info("Attempt {} to confirm room availability for room {}",
                    context.getRetryCount() + 1, roomId);

            boolean result = hotelServiceClient.confirmRoomAvailability(roomId, request);

            if (!result) {
                return false;
            }

            return result;
        });
    }

    @Transactional(readOnly = true)
    public Page<BookingDto> getUserBookings(Long userId, Pageable pageable) {
        Page<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return bookings.map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getUserBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDto getBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        return convertToDto(booking);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
            try {
                hotelServiceClient.releaseRoom(booking.getRoomId(), booking.getRequestId());
            } catch (Exception e) {
                log.error("Error releasing room for booking {}: {}", bookingId, e.getMessage());
            }
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        log.info("Booking {} cancelled by user {}", bookingId, userId);
    }

    private Long selectRecommendedRoom(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            List<RoomDto> recommendedRooms = hotelServiceClient.getRecommendedRooms();

            for (RoomDto room : recommendedRooms) {
                List<Booking> conflicts = bookingRepository.findConflictingBookings(
                        room.getId(), startDate, endDate);

                if (conflicts.isEmpty()) {
                    return room.getId();
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Error selecting recommended room: {}", e.getMessage());
            return null;
        }
    }

    private BookingDto convertToDto(Booking booking) {
        return BookingDto.builder()
                .id(booking.getId())
                .userId(booking.getUser().getId())
                .roomId(booking.getRoomId())
                .startDate(booking.getStartDate())
                .endDate(booking.getEndDate())
                .status(booking.getStatus().name())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
