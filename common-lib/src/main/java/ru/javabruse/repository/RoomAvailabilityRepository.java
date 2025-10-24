package ru.javabruse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.javabruse.entity.RoomAvailability;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomAvailabilityRepository extends JpaRepository<RoomAvailability, Long> {
    
    @Query("SELECT ra FROM RoomAvailability ra WHERE ra.room.id = :roomId AND " +
           "ra.isBlocked = true AND " +
           "((ra.startDate <= :endDate AND ra.endDate >= :startDate))")
    List<RoomAvailability> findConflictingBlocks(@Param("roomId") Long roomId,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);
    
    Optional<RoomAvailability> findByRequestId(String requestId);
    
    void deleteByRequestId(@Param("requestId") String requestId);
}
