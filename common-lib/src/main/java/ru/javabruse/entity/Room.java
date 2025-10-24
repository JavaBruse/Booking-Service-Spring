package ru.javabruse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "rooms", indexes = {
    @Index(name = "idx_room_hotel_id", columnList = "hotel_id"),
    @Index(name = "idx_room_available", columnList = "available"),
    @Index(name = "idx_room_times_booked", columnList = "times_booked"),
    @Index(name = "idx_room_number", columnList = "number")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
    
    @Column(nullable = false)
    private String number;
    
    @Column(nullable = false)
    private Boolean available;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer timesBooked = 0;
    
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RoomAvailability> availabilityBlocks;
}
