package ru.javabruse.hotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.javabruse.dto.HotelDto;
import ru.javabruse.dto.RoomAvailabilityRequest;
import ru.javabruse.dto.RoomDto;
import ru.javabruse.entity.Hotel;
import ru.javabruse.entity.Room;
import ru.javabruse.entity.RoomAvailability;
import ru.javabruse.repository.HotelRepository;
import ru.javabruse.repository.RoomAvailabilityRepository;
import ru.javabruse.repository.RoomRepository;
import ru.javabruse.hotel.service.JwtService;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class HotelManagementServiceIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomAvailabilityRepository roomAvailabilityRepository;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private Hotel testHotel;
    private Room testRoom;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        roomAvailabilityRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();

        testHotel = Hotel.builder()
                .name("Test Hotel")
                .address("Test Address")
                .build();
        testHotel = hotelRepository.save(testHotel);
        testRoom = Room.builder()
                .hotel(testHotel)
                .number("101")
                .available(true)
                .timesBooked(0)
                .build();
        testRoom = roomRepository.save(testRoom);
        adminToken = jwtService.generateToken(1L, "admin", "ADMIN");
        userToken = jwtService.generateToken(2L, "user", "USER");
    }

    @Test
    void testHotelCrudOperations() throws Exception {
        HotelDto newHotel = HotelDto.builder()
                .name("New Hotel")
                .address("New Address")
                .build();

        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newHotel)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Hotel"))
                .andExpect(jsonPath("$.address").value("New Address"));

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].address").exists());
    }

    @Test
    void testRoomCrudOperations() throws Exception {
        RoomDto newRoom = RoomDto.builder()
                .hotelId(testHotel.getId())
                .number("102")
                .available(true)
                .timesBooked(0)
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newRoom)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("102"))
                .andExpect(jsonPath("$.hotelId").value(testHotel.getId()));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].number").exists())
                .andExpect(jsonPath("$[0].available").value(true));

        mockMvc.perform(get("/api/rooms/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].timesBooked").value(0));
    }

    @Test
    void testRoomAvailabilityConfirmation() throws Exception {
        RoomAvailabilityRequest request = RoomAvailabilityRequest.builder()
                .roomId(testRoom.getId())
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(3))
                .bookingId("booking-123")
                .requestId("request-123")
                .build();

        mockMvc.perform(post("/api/rooms/" + testRoom.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        assertTrue(roomAvailabilityRepository.findByRequestId("request-123").isPresent());
    }

    @Test
    void testRoomAvailabilityIdempotency() throws Exception {
        RoomAvailabilityRequest request = RoomAvailabilityRequest.builder()
                .roomId(testRoom.getId())
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(3))
                .bookingId("booking-123")
                .requestId("request-123")
                .build();

        mockMvc.perform(post("/api/rooms/" + testRoom.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        mockMvc.perform(post("/api/rooms/" + testRoom.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        assertEquals(1, roomAvailabilityRepository.count());
    }

    @Test
    void testRoomAvailabilityConflict() throws Exception {
        // Create first availability block
        RoomAvailabilityRequest firstRequest = RoomAvailabilityRequest.builder()
                .roomId(testRoom.getId())
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(3))
                .bookingId("booking-1")
                .requestId("request-1")
                .build();

        mockMvc.perform(post("/api/rooms/" + testRoom.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        RoomAvailabilityRequest conflictingRequest = RoomAvailabilityRequest.builder()
                .roomId(testRoom.getId())
                .startDate(LocalDateTime.now().plusDays(2))
                .endDate(LocalDateTime.now().plusDays(4))
                .bookingId("booking-2")
                .requestId("request-2")
                .build();

        mockMvc.perform(post("/api/rooms/" + testRoom.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        assertEquals(1, roomAvailabilityRepository.count());
    }

    @Test
    void testRoomRelease() throws Exception {
        // Create availability block
        RoomAvailability availability = RoomAvailability.builder()
                .room(testRoom)
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(3))
                .isBlocked(true)
                .bookingId("booking-123")
                .requestId("request-123")
                .build();
        roomAvailabilityRepository.save(availability);

        mockMvc.perform(post("/api/rooms/" + testRoom.getId() + "/release")
                        .param("requestId", "request-123"))
                .andExpect(status().isOk());

        assertFalse(roomAvailabilityRepository.findByRequestId("request-123").isPresent());
    }

    @Test
    void testTimesBookedIncrement() throws Exception {
        assertEquals(0, testRoom.getTimesBooked());

        testRoom.setTimesBooked(testRoom.getTimesBooked() + 1);
        roomRepository.save(testRoom);

        Room updatedRoom = roomRepository.findById(testRoom.getId()).orElseThrow();
        assertEquals(1, updatedRoom.getTimesBooked());
    }

    @Test
    void testConcurrentAvailabilityRequests() throws Exception {
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CompletableFuture<Void>[] futures = new CompletableFuture[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    RoomAvailabilityRequest request = RoomAvailabilityRequest.builder()
                            .roomId(testRoom.getId())
                            .startDate(LocalDateTime.now().plusDays(threadId + 1))
                            .endDate(LocalDateTime.now().plusDays(threadId + 3))
                            .bookingId("booking-" + threadId)
                            .requestId("request-" + threadId)
                            .build();

                    mockMvc.perform(post("/api/rooms/" + testRoom.getId() + "/confirm-availability")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    fail("Concurrent availability request failed: " + e.getMessage());
                }
            }, executor);
        }

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(roomAvailabilityRepository.count() > 0);
        assertTrue(roomAvailabilityRepository.count() <= numberOfThreads);
    }

    @Test
    void testValidationErrors() throws Exception {
        HotelDto invalidHotel = HotelDto.builder()
                .address("Test Address")
                .build();

        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidHotel)))
                .andExpect(status().isBadRequest());

        RoomDto invalidRoom = RoomDto.builder()
                .number("102")
                .available(true)
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRoom)))
                .andExpect(status().isBadRequest());
    }
}
