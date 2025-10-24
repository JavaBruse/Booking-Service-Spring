package ru.javabruse.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.javabruse.booking.client.HotelServiceClient;
import ru.javabruse.booking.config.SecurityConfig;
import ru.javabruse.booking.dto.BookingRequest;
import ru.javabruse.booking.entity.Booking;
import ru.javabruse.booking.entity.User;
import ru.javabruse.booking.repository.BookingRepository;
import ru.javabruse.booking.repository.UserRepository;
import ru.javabruse.booking.service.JwtService;
import ru.javabruse.dto.RoomDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(SecurityConfig.class)
class ConcurrentBookingTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private HotelServiceClient hotelServiceClient;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private User testUser;
    private String userToken;

    @BeforeEach
    @Transactional
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        bookingRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = User.builder()
                .username("testuser")
                .password("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi")
                .role(User.Role.USER)
                .build();
        testUser = userRepository.save(testUser);

        userToken = jwtService.generateToken(testUser.getId(), testUser.getUsername(), testUser.getRole().name());

        List<RoomDto> mockRooms = new ArrayList<>();
        RoomDto mockRoom1 = new RoomDto();
        mockRoom1.setId(1L);
        mockRoom1.setNumber("101");
        mockRoom1.setAvailable(true);
        mockRooms.add(mockRoom1);
        RoomDto mockRoom2 = new RoomDto();
        mockRoom2.setId(2L);
        mockRoom2.setNumber("102");
        mockRoom2.setAvailable(true);
        mockRooms.add(mockRoom2);
        lenient().when(hotelServiceClient.getRecommendedRooms())
                .thenReturn(mockRooms);
        lenient().when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenReturn(true);
        lenient().doNothing().when(hotelServiceClient).releaseRoom(anyLong(), anyString());
    }

    @Test
    void testConcurrentBookingsSameRoom() throws Exception {
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenAnswer(invocation -> {
                    int currentCount = successCount.get();
                    if (currentCount < 3) {
                        successCount.incrementAndGet();
                        return true;
                    } else {
                        failureCount.incrementAndGet();
                        return false;
                    }
                });

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    BookingRequest bookingRequest = BookingRequest.builder()
                            .roomId(1L)
                            .startDate(LocalDateTime.now().plusDays(1))
                            .endDate(LocalDateTime.now().plusDays(3))
                            .autoSelect(false)
                            .build();

                    mockMvc.perform(post("/api/booking")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bookingRequest)))
                            .andExpect(status().isCreated());
                } catch (Exception e) {
                    fail("Concurrent booking failed: " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        executor.shutdown();
        List<Booking> bookings = bookingRepository.findAll();
        assertTrue(bookings.size() > 0);
        assertTrue(bookings.size() <= numberOfThreads);
        long confirmedCount = bookings.stream()
                .mapToLong(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED ? 1 : 0)
                .sum();
        long cancelledCount = bookings.stream()
                .mapToLong(b -> b.getStatus() == Booking.BookingStatus.CANCELLED ? 1 : 0)
                .sum();
        assertTrue(confirmedCount > 0, "At least one booking should be confirmed");
        assertTrue(cancelledCount > 0, "At least one booking should be cancelled due to conflicts");
        assertEquals(numberOfThreads, confirmedCount + cancelledCount, "All bookings should be processed");
        verify(hotelServiceClient, times(numberOfThreads)).confirmRoomAvailability(anyLong(), any());
    }

    @Test
    void testConcurrentBookingsDifferentRooms() throws Exception {
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenReturn(true);
        for (int i = 0; i < numberOfThreads; i++) {
            final int roomId = i + 1;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    BookingRequest bookingRequest = BookingRequest.builder()
                            .roomId((long) roomId)
                            .startDate(LocalDateTime.now().plusDays(1))
                            .endDate(LocalDateTime.now().plusDays(3))
                            .autoSelect(false)
                            .build();

                    mockMvc.perform(post("/api/booking")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bookingRequest)))
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.status").value("CONFIRMED"));
                } catch (Exception e) {
                    fail("Concurrent booking failed: " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        executor.shutdown();
        List<Booking> bookings = bookingRepository.findAll();
        assertEquals(numberOfThreads, bookings.size());
        bookings.forEach(booking -> assertEquals(Booking.BookingStatus.CONFIRMED, booking.getStatus()));
        verify(hotelServiceClient, times(numberOfThreads)).confirmRoomAvailability(anyLong(), any());
    }

    @Test
    void testConcurrentAutoSelectBookings() throws Exception {
        int numberOfThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<RoomDto> mockRooms = List.of(
                createRoomDto(1L, "101"),
                createRoomDto(2L, "102"));
        when(hotelServiceClient.getRecommendedRooms())
                .thenReturn(mockRooms);
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenReturn(true);
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    BookingRequest bookingRequest = BookingRequest.builder()
                            .startDate(LocalDateTime.now().plusDays(1))
                            .endDate(LocalDateTime.now().plusDays(3))
                            .autoSelect(true)
                            .build();
                    mockMvc.perform(post("/api/booking")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bookingRequest)))
                            .andExpect(status().isCreated());
                } catch (Exception e) {
                    System.out.println("Auto-select booking failed: " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        executor.shutdown();

        List<Booking> bookings = bookingRepository.findAll();
        assertTrue(bookings.size() > 0, "At least some bookings should be created");
    }

    private RoomDto createRoomDto(Long id, String number) {
        RoomDto room = new RoomDto();
        room.setId(id);
        room.setNumber(number);
        room.setAvailable(true);
        return room;
    }

    @Test
    void testRaceConditionInBookingCreation() throws Exception {
        int numberOfThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger requestCounter = new AtomicInteger(0);
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenAnswer(invocation -> {
                    int currentCount = requestCounter.incrementAndGet();
                    return currentCount <= 5;
                });

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    BookingRequest bookingRequest = BookingRequest.builder()
                            .roomId(1L)
                            .startDate(LocalDateTime.now().plusDays(1))
                            .endDate(LocalDateTime.now().plusDays(3))
                            .autoSelect(false)
                            .build();

                    mockMvc.perform(post("/api/booking")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bookingRequest)))
                            .andExpect(status().isCreated());
                } catch (Exception e) {
                    fail("Race condition test failed: " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        executor.shutdown();
        List<Booking> bookings = bookingRepository.findAll();
        assertEquals(numberOfThreads, bookings.size(), "All booking requests should create booking records");
        long confirmedCount = bookings.stream()
                .mapToLong(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED ? 1 : 0)
                .sum();
        long cancelledCount = bookings.stream()
                .mapToLong(b -> b.getStatus() == Booking.BookingStatus.CANCELLED ? 1 : 0)
                .sum();

        assertTrue(confirmedCount > 0, "Some bookings should be confirmed");
        assertTrue(cancelledCount > 0, "Some bookings should be cancelled");
        assertEquals(numberOfThreads, confirmedCount + cancelledCount, "All bookings should be processed");
        verify(hotelServiceClient, times(numberOfThreads)).confirmRoomAvailability(anyLong(), any());
    }

    @Test
    void testConcurrentBookingWithRetryMechanism() throws Exception {
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    if (count <= numberOfThreads) {
                        throw new RuntimeException("Temporary failure");
                    }
                    return true;
                });
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    BookingRequest bookingRequest = BookingRequest.builder()
                            .roomId(1L)
                            .startDate(LocalDateTime.now().plusDays(1))
                            .endDate(LocalDateTime.now().plusDays(3))
                            .autoSelect(false)
                            .build();
                    mockMvc.perform(post("/api/booking")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bookingRequest)))
                            .andExpect(status().isCreated());
                } catch (Exception e) {
                    System.out.println("Booking failed (expected): " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        executor.shutdown();
        verify(hotelServiceClient, atLeast(numberOfThreads)).confirmRoomAvailability(anyLong(), any());
    }
}
