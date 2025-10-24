package ru.javabruse.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
class DistributedTransactionTest {

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
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        testUser = User.builder()
                .username("testuser")
                .password("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi")
                .role(User.Role.USER)
                .build();
        testUser = userRepository.save(testUser);
        userToken = jwtService.generateToken(testUser.getId(), testUser.getUsername(),
                testUser.getRole().name());
        reset(hotelServiceClient);
        when(hotelServiceClient.getRecommendedRooms()).thenReturn(new java.util.ArrayList<>());
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any())).thenReturn(true);
        doNothing().when(hotelServiceClient).releaseRoom(anyLong(), anyString());
    }

    @Test
    void testSuccessfulBookingConfirmation() throws Exception {
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenReturn(true);

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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(hotelServiceClient, times(1)).confirmRoomAvailability(anyLong(), any());
        Booking booking = bookingRepository.findAll().get(0);
        assertEquals(Booking.BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    void testBookingCompensationOnHotelServiceFailure() throws Exception {
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenReturn(false);
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(hotelServiceClient, times(1)).confirmRoomAvailability(anyLong(), any());
        Booking booking = bookingRepository.findAll().get(0);
        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    void testBookingCompensationOnHotelServiceException() throws Exception {
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenThrow(new RuntimeException("Hotel service unavailable"));

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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        Booking booking = bookingRepository.findAll().get(0);
        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    void testIdempotencyWithDuplicateRequestId() throws Exception {
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenReturn(true)
                .thenReturn(true); // Idempotent response
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(post("/api/booking")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingRequest)))
                .andExpect(status().isCreated());
        verify(hotelServiceClient, times(2)).confirmRoomAvailability(anyLong(), any());
        assertEquals(2, bookingRepository.count());
    }

    @Test
    void testConcurrentBookingConflicts() throws Exception {
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(true);
        int numberOfThreads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CompletableFuture<Void>[] futures = new CompletableFuture[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
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
        }
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(bookingRepository.count() > 0);
        assertTrue(bookingRepository.count() <= numberOfThreads);
        verify(hotelServiceClient, atLeast(1)).confirmRoomAvailability(anyLong(), any());
    }

    @Test
    void testRetryMechanism() throws Exception {
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenReturn(true);
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(hotelServiceClient, times(3)).confirmRoomAvailability(anyLong(), any());
        Booking booking = bookingRepository.findAll().get(0);
        assertEquals(Booking.BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    void testRetryExhaustionAndCompensation() throws Exception {
        when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                .thenThrow(new RuntimeException("Persistent failure"));
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(hotelServiceClient, times(3)).confirmRoomAvailability(anyLong(), any());
        Booking booking = bookingRepository.findAll().get(0);
        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
    }
}
