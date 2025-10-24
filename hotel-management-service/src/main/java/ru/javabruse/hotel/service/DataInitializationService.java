package ru.javabruse.hotel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javabruse.entity.Hotel;
import ru.javabruse.entity.Room;
import ru.javabruse.repository.HotelRepository;
import ru.javabruse.repository.RoomRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitializationService implements CommandLineRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Starting hotel data initialization...");
        Hotel hotel1 = createHotel("Grand Hotel Moscow", "Red Square, 1, Moscow");
        Hotel hotel2 = createHotel("Hotel St. Petersburg", "Nevsky Prospect, 25, St. Petersburg");
        Hotel hotel3 = createHotel("Sochi Resort", "Black Sea Coast, Sochi");
        createRoomsForHotel(hotel1, 5);
        createRoomsForHotel(hotel2, 8);
        createRoomsForHotel(hotel3, 12);
        log.info("Hotel data initialization completed");
    }

    private Hotel createHotel(String name, String address) {
        Hotel hotel = Hotel.builder()
                .name(name)
                .address(address)
                .build();
        return hotelRepository.save(hotel);
    }

    private void createRoomsForHotel(Hotel hotel, int roomCount) {
        for (int i = 1; i <= roomCount; i++) {
            Room room = Room.builder()
                    .hotel(hotel)
                    .number(String.format("%d%02d", hotel.getId(), i))
                    .available(true)
                    .timesBooked(0)
                    .build();
            roomRepository.save(room);
        }
        log.info("Created {} rooms for hotel: {}", roomCount, hotel.getName());
    }
}
