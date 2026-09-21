package com.eventbooking.integration;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.eventbooking.dto.BookingRequest;
import com.eventbooking.dto.EventRequest;
import com.eventbooking.model.BookingStatus;

import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EventBookingIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16")
                    .withDatabaseName("eventbooking_test")
                    .withUsername("testuser")
                    .withPassword("testpass");

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateEventBookTicketsAndCancelBooking()
            throws Exception {

        EventRequest eventRequest = new EventRequest();
        eventRequest.setTitle("Test Event");
        eventRequest.setDescription("Integration test event");
        eventRequest.setLocation("Melbourne");
        eventRequest.setEventDate(
                LocalDateTime.now().plusDays(5)
        );
        eventRequest.setTotalSeats(10);

        String eventResponse = mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                eventRequest
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.title")
                                .value("Test Event")
                )
                .andExpect(
                        jsonPath("$.availableSeats")
                                .value(10)
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        long eventId = objectMapper
                .readTree(eventResponse)
                .get("id")
                .asLong();

        BookingRequest bookingRequest =
                new BookingRequest();

        bookingRequest.setEventId(eventId);
        bookingRequest.setCustomerName("Test User");
        bookingRequest.setCustomerEmail(
                "test@example.com"
        );
        bookingRequest.setTicketQuantity(3);

        String bookingResponse = mockMvc.perform(
                        post("/api/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                bookingRequest
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.status")
                                .value(
                                        BookingStatus.CONFIRMED.name()
                                )
                )
                .andExpect(
                        jsonPath("$.ticketQuantity")
                                .value(3)
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        long bookingId = objectMapper
                .readTree(bookingResponse)
                .get("id")
                .asLong();

        mockMvc.perform(
                        get(
                                "/api/events/{id}",
                                eventId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.availableSeats")
                                .value(7)
                )
                .andExpect(
                        jsonPath("$.bookedSeats")
                                .value(3)
                );

        mockMvc.perform(
                        patch(
                                "/api/bookings/{id}/cancel",
                                bookingId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value(
                                        BookingStatus.CANCELLED.name()
                                )
                );

        mockMvc.perform(
                        get(
                                "/api/events/{id}",
                                eventId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.availableSeats")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.bookedSeats")
                                .value(0)
                );
    }

    @Test
    void shouldRejectOverbooking()
            throws Exception {

        EventRequest eventRequest = new EventRequest();
        eventRequest.setTitle("Small Event");
        eventRequest.setDescription("Capacity test");
        eventRequest.setLocation("Melbourne");
        eventRequest.setEventDate(
                LocalDateTime.now().plusDays(3)
        );
        eventRequest.setTotalSeats(2);

        String eventResponse = mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                eventRequest
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long eventId = objectMapper
                .readTree(eventResponse)
                .get("id")
                .asLong();

        BookingRequest bookingRequest =
                new BookingRequest();

        bookingRequest.setEventId(eventId);
        bookingRequest.setCustomerName("Test User");
        bookingRequest.setCustomerEmail(
                "test@example.com"
        );
        bookingRequest.setTicketQuantity(3);

        mockMvc.perform(
                        post("/api/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                bookingRequest
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath(
                                "$.message",
                                is(
                                        "requested tickets exceed available seats"
                                )
                        )
                );
    }
}