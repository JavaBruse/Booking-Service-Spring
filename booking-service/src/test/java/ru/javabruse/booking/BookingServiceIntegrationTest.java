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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.javabruse.booking.client.HotelServiceClient;
import ru.javabruse.booking.config.SecurityConfig;
import ru.javabruse.dto.RoomDto;
import ru.javabruse.booking.dto.BookingRequest;
import ru.javabruse.booking.dto.UserLoginRequest;
import ru.javabruse.booking.dto.UserRegistrationRequest;
import ru.javabruse.booking.entity.Booking;
import ru.javabruse.booking.entity.User;
import ru.javabruse.booking.repository.BookingRepository;
import ru.javabruse.booking.repository.UserRepository;
import ru.javabruse.booking.service.JwtService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(SecurityConfig.class)
class BookingServiceIntegrationTest {

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
                                .password("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi") // "password"
                                .role(User.Role.USER)
                                .build();
                testUser = userRepository.save(testUser);
                userToken = jwtService.generateToken(testUser.getId(), testUser.getUsername(),
                                testUser.getRole().name());

                List<RoomDto> mockRooms = new ArrayList<>();
                RoomDto mockRoom = new RoomDto();
                mockRoom.setId(1L);
                mockRoom.setNumber("101");
                mockRoom.setAvailable(true);
                mockRooms.add(mockRoom);

                lenient().when(hotelServiceClient.getRecommendedRooms()).thenReturn(mockRooms);
                lenient().when(hotelServiceClient.confirmRoomAvailability(anyLong(), any())).thenReturn(true);
                lenient().doNothing().when(hotelServiceClient).releaseRoom(anyLong(), anyString());
        }

        @Test
        void testUserRegistrationAndLogin() throws Exception {
                UserRegistrationRequest registrationRequest = UserRegistrationRequest.builder()
                                .username("newuser")
                                .password("password123")
                                .build();

                mockMvc.perform(post("/api/user/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registrationRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.username").value("newuser"))
                                .andExpect(jsonPath("$.token").exists())
                                .andExpect(jsonPath("$.role").value("USER"));

                UserLoginRequest loginRequest = UserLoginRequest.builder()
                                .username("newuser")
                                .password("password123")
                                .build();

                mockMvc.perform(post("/api/user/auth")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.username").value("newuser"))
                                .andExpect(jsonPath("$.token").exists())
                                .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        void testBookingCreationSuccess() throws Exception {
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
                                .andExpect(jsonPath("$.roomId").value(1))
                                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                                .andExpect(jsonPath("$.status").exists());

                assertEquals(1, bookingRepository.count());
                Booking booking = bookingRepository.findAll().get(0);
                assertEquals(1L, booking.getRoomId());
                assertEquals(testUser.getId(), booking.getUser().getId());
        }

        @Test
        void testAutoSelectRoom() throws Exception {
                BookingRequest bookingRequest = BookingRequest.builder()
                                .startDate(LocalDateTime.now().plusDays(1))
                                .endDate(LocalDateTime.now().plusDays(3))
                                .autoSelect(true)
                                .build();

                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bookingRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.roomId").exists())
                                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                                .andExpect(jsonPath("$.status").exists());
        }

        @Test
        void testGetUserBookings() throws Exception {
                Booking booking = Booking.builder()
                                .user(testUser)
                                .roomId(1L)
                                .startDate(LocalDateTime.now().plusDays(1))
                                .endDate(LocalDateTime.now().plusDays(3))
                                .status(Booking.BookingStatus.CONFIRMED)
                                .createdAt(LocalDateTime.now())
                                .requestId("test-request-id")
                                .build();
                bookingRepository.save(booking);

                MvcResult result = mockMvc.perform(get("/api/bookings")
                                .header("Authorization", "Bearer " + userToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andReturn();

                System.out.println("GetBookings Status: " + result.getResponse().getStatus());
                System.out.println("GetBookings Response: " + result.getResponse().getContentAsString());
        }

        @Test
        void testBookingCancellation() throws Exception {
                Booking booking = Booking.builder()
                                .user(testUser)
                                .roomId(1L)
                                .startDate(LocalDateTime.now().plusDays(1))
                                .endDate(LocalDateTime.now().plusDays(3))
                                .status(Booking.BookingStatus.CONFIRMED)
                                .createdAt(LocalDateTime.now())
                                .requestId("test-request-id")
                                .build();
                booking = bookingRepository.save(booking);

                mockMvc.perform(delete("/api/booking/{id}", booking.getId())
                                .header("Authorization", "Bearer " + userToken))
                                .andExpect(status().isOk());

                Booking cancelledBooking = bookingRepository.findById(booking.getId()).orElseThrow();
                assertEquals(Booking.BookingStatus.CANCELLED, cancelledBooking.getStatus());
        }

        @Test
        void testUnauthorizedAccess() throws Exception {
                BookingRequest bookingRequest = BookingRequest.builder()
                                .roomId(1L)
                                .startDate(LocalDateTime.now().plusDays(1))
                                .endDate(LocalDateTime.now().plusDays(3))
                                .autoSelect(false)
                                .build();

                mockMvc.perform(post("/api/booking")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bookingRequest)))
                                .andExpect(status().is4xxClientError());

                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer invalid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bookingRequest)))
                                .andExpect(status().is4xxClientError());
        }

        @Test
        void testConcurrentBookings() throws Exception {
                int numberOfThreads = 10;
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
        }

        @Test
        void testBookingValidation() throws Exception {
                BookingRequest invalidRequest = BookingRequest.builder()
                                .roomId(1L)
                                .endDate(LocalDateTime.now().plusDays(3))
                                .autoSelect(false)
                                .build();

                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                                .andExpect(status().isBadRequest());

                BookingRequest invalidDateRequest = BookingRequest.builder()
                                .roomId(1L)
                                .startDate(LocalDateTime.now().plusDays(3))
                                .endDate(LocalDateTime.now().plusDays(1))
                                .autoSelect(false)
                                .build();

                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidDateRequest)))
                                .andExpect(status().isBadRequest());
        }
}
