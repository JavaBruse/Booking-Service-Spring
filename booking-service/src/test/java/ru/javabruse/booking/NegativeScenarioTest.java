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
import ru.javabruse.booking.dto.UserLoginRequest;
import ru.javabruse.booking.dto.UserRegistrationRequest;
import ru.javabruse.booking.entity.Booking;
import ru.javabruse.booking.entity.User;
import ru.javabruse.booking.repository.BookingRepository;
import ru.javabruse.booking.repository.UserRepository;
import ru.javabruse.booking.service.JwtService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(SecurityConfig.class)
class NegativeScenarioTest {

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
                lenient().when(hotelServiceClient.getRecommendedRooms()).thenReturn(new java.util.ArrayList<>());
                lenient().when(hotelServiceClient.confirmRoomAvailability(anyLong(), any())).thenReturn(true);
                lenient().doNothing().when(hotelServiceClient).releaseRoom(anyLong(), anyString());
        }

        @Test
        void testInvalidUserRegistration() throws Exception {
                UserRegistrationRequest existingUser = UserRegistrationRequest.builder()
                                .username("testuser")
                                .password("password123")
                                .build();
                mockMvc.perform(post("/api/user/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(existingUser)))
                                .andExpect(status().isInternalServerError());
                UserRegistrationRequest emptyUsername = UserRegistrationRequest.builder()
                                .username("")
                                .password("password123")
                                .build();
                mockMvc.perform(post("/api/user/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(emptyUsername)))
                                .andExpect(status().isBadRequest());
                UserRegistrationRequest emptyPassword = UserRegistrationRequest.builder()
                                .username("newuser")
                                .password("")
                                .build();
                mockMvc.perform(post("/api/user/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(emptyPassword)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testInvalidUserLogin() throws Exception {
                UserLoginRequest nonExistentUser = UserLoginRequest.builder()
                                .username("nonexistent")
                                .password("password123")
                                .build();
                mockMvc.perform(post("/api/user/auth")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(nonExistentUser)))
                                .andExpect(status().isInternalServerError());
                UserLoginRequest wrongPassword = UserLoginRequest.builder()
                                .username("testuser")
                                .password("wrongpassword")
                                .build();
                mockMvc.perform(post("/api/user/auth")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(wrongPassword)))
                                .andExpect(status().isInternalServerError());
                UserLoginRequest emptyCredentials = UserLoginRequest.builder()
                                .username("")
                                .password("")
                                .build();
                mockMvc.perform(post("/api/user/auth")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(emptyCredentials)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testInvalidBookingRequest() throws Exception {
                BookingRequest invalidDateRange = BookingRequest.builder()
                                .roomId(1L)
                                .startDate(LocalDateTime.now().plusDays(3))
                                .endDate(LocalDateTime.now().plusDays(1))
                                .autoSelect(false)
                                .build();
                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidDateRange)))
                                .andExpect(status().isBadRequest());
                BookingRequest pastDates = BookingRequest.builder()
                                .roomId(1L)
                                .startDate(LocalDateTime.now().minusDays(1))
                                .endDate(LocalDateTime.now().plusDays(1))
                                .autoSelect(false)
                                .build();
                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(pastDates)))
                                .andExpect(status().isBadRequest());
                BookingRequest missingDates = BookingRequest.builder()
                                .roomId(1L)
                                .autoSelect(false)
                                .build();
                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(missingDates)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testHotelServiceTimeout() throws Exception {
                when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                                .thenThrow(new RuntimeException("Connection timeout"));
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
        void testHotelServiceUnavailable() throws Exception {
                when(hotelServiceClient.confirmRoomAvailability(anyLong(), any()))
                                .thenThrow(new RuntimeException("Service unavailable"));
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
        void testAutoSelectWithNoAvailableRooms() throws Exception {
                when(hotelServiceClient.getRecommendedRooms())
                                .thenReturn(java.util.Collections.emptyList());
                BookingRequest bookingRequest = BookingRequest.builder()
                                .startDate(LocalDateTime.now().plusDays(1))
                                .endDate(LocalDateTime.now().plusDays(3))
                                .autoSelect(true)
                                .build();
                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bookingRequest)))
                                .andExpect(status().isInternalServerError());
        }

        @Test
        void testBookingNotFound() throws Exception {
                mockMvc.perform(get("/api/booking/999")
                                .header("Authorization", "Bearer " + userToken))
                                .andExpect(status().isInternalServerError());
                mockMvc.perform(delete("/api/booking/999")
                                .header("Authorization", "Bearer " + userToken))
                                .andExpect(status().isInternalServerError());
        }

        @Test
        void testUnauthorizedBookingAccess() throws Exception {
                User anotherUser = User.builder()
                                .username("anotheruser")
                                .password("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi")
                                .role(User.Role.USER)
                                .build();
                anotherUser = userRepository.save(anotherUser);
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
                String anotherUserToken = jwtService.generateToken(anotherUser.getId(), anotherUser.getUsername(),
                                anotherUser.getRole().name());
                mockMvc.perform(get("/api/booking/" + booking.getId())
                                .header("Authorization", "Bearer " + anotherUserToken))
                                .andExpect(status().isInternalServerError());
                mockMvc.perform(delete("/api/booking/" + booking.getId())
                                .header("Authorization", "Bearer " + anotherUserToken))
                                .andExpect(status().isInternalServerError());
        }

        @Test
        void testInvalidJwtToken() throws Exception {
                BookingRequest bookingRequest = BookingRequest.builder()
                                .roomId(1L)
                                .startDate(LocalDateTime.now().plusDays(1))
                                .endDate(LocalDateTime.now().plusDays(3))
                                .autoSelect(false)
                                .build();
                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer invalid.jwt.token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bookingRequest)))
                                .andExpect(status().isUnauthorized());
                String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImV4cCI6MTYwMDAwMDAwMCwiaWF0IjoxNjAwMDAwMDAwfQ.invalid";
                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + expiredToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bookingRequest)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void testMalformedJsonRequest() throws Exception {
                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ invalid json }"))
                                .andExpect(status().isBadRequest());
                BookingRequest bookingRequest = BookingRequest.builder()
                                .roomId(1L)
                                .startDate(LocalDateTime.now().plusDays(1))
                                .endDate(LocalDateTime.now().plusDays(3))
                                .autoSelect(false)
                                .build();
                mockMvc.perform(post("/api/booking")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(objectMapper.writeValueAsString(bookingRequest)))
                                .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void testDatabaseConstraintViolations() throws Exception {
                UserRegistrationRequest duplicateUser = UserRegistrationRequest.builder()
                                .username("testuser") // Already exists
                                .password("password123")
                                .build();
                mockMvc.perform(post("/api/user/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateUser)))
                                .andExpect(status().isInternalServerError());
        }
}
