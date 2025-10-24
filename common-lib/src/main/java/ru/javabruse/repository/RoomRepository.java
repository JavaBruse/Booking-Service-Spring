package ru.javabruse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.javabruse.entity.Room;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    
    List<Room> findByHotelIdAndAvailableTrue(Long hotelId);
    
    @Query("SELECT r FROM Room r WHERE r.available = true ORDER BY r.timesBooked ASC, r.id ASC")
    List<Room> findAvailableRoomsOrderedByTimesBooked();
    
    @Query("SELECT r FROM Room r WHERE r.available = true AND r.id NOT IN " +
           "(SELECT ra.room.id FROM RoomAvailability ra WHERE " +
           "ra.isBlocked = true AND " +
           "((ra.startDate <= :endDate AND ra.endDate >= :startDate))) " +
           "ORDER BY r.timesBooked ASC, r.id ASC")
    List<Room> findAvailableRoomsForPeriod(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);
}
