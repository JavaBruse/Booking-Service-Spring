package ru.javabruse.booking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.javabruse.booking.entity.Booking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    Optional<Booking> findByIdAndUserId(Long id, Long userId);
    
    @Query("SELECT b FROM Booking b WHERE b.requestId = :requestId")
    Optional<Booking> findByRequestId(@Param("requestId") String requestId);
    
    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND " +
           "b.status = 'CONFIRMED' AND " +
           "((b.startDate <= :endDate AND b.endDate >= :startDate))")
    List<Booking> findConflictingBookings(@Param("roomId") Long roomId,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);
}
