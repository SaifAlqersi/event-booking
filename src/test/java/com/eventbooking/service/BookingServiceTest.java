package com.eventbooking.service;

import com.eventbooking.dto.BookingRequest;
import com.eventbooking.dto.BookingResponse;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.model.Booking;
import com.eventbooking.model.BookingStatus;
import com.eventbooking.model.Event;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private BookingService bookingService;

    private Event event;

    @BeforeEach
    void setUp() {
        event = new Event(
                "DevOps Conference",
                "DevOps event",
                "Melbourne",
                LocalDateTime.now().plusDays(10),
                100
        );
    }

    @Test
    void shouldCreateBookingAndReduceSeats() {
        when(eventRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(event));

        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response =
                bookingService.createBooking(request(1L, 3));

        assertEquals(3, response.ticketQuantity());
        assertEquals(BookingStatus.CONFIRMED, response.status());
        assertEquals(97, event.getAvailableSeats());

        verify(eventRepository).save(event);
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void shouldRejectBookingWhenEventDoesNotExist() {
        when(eventRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> bookingService.createBooking(request(99L, 1))
        );
    }

    @Test
    void shouldRejectBookingWhenSeatsAreInsufficient() {
        event.setAvailableSeats(2);

        when(eventRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(event));

        assertThrows(
                BadRequestException.class,
                () -> bookingService.createBooking(request(1L, 3))
        );

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldRejectBookingForPastEvent() {
        Event pastEvent = new Event(
                "Old Event",
                "Past event",
                "Melbourne",
                LocalDateTime.now().minusDays(1),
                100
        );

        when(eventRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(pastEvent));

        assertThrows(
                BadRequestException.class,
                () -> bookingService.createBooking(request(1L, 1))
        );
    }

    @Test
    void shouldCancelBookingAndRestoreSeats() {
        event.setAvailableSeats(97);

        Booking booking = new Booking(
                event,
                "Ahmed Ali",
                "ahmed@example.com",
                3
        );

        when(bookingRepository.findById(1L))
                .thenReturn(Optional.of(booking));

        when(eventRepository.findByIdForUpdate(any()))
                .thenReturn(Optional.of(event));

        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response =
                bookingService.cancelBooking(1L);

        assertEquals(BookingStatus.CANCELLED, response.status());
        assertEquals(100, event.getAvailableSeats());
        assertNotNull(response.cancelledAt());
    }

    @Test
    void shouldRejectCancellingAlreadyCancelledBooking() {
        Booking booking = new Booking(
                event,
                "Ahmed Ali",
                "ahmed@example.com",
                2
        );

        booking.cancel();

        when(bookingRepository.findById(1L))
                .thenReturn(Optional.of(booking));

        assertThrows(
                BadRequestException.class,
                () -> bookingService.cancelBooking(1L)
        );
    }

    private BookingRequest request(Long eventId, int quantity) {
        BookingRequest request = new BookingRequest();
        request.setEventId(eventId);
        request.setCustomerName("Ahmed Ali");
        request.setCustomerEmail("ahmed@example.com");
        request.setTicketQuantity(quantity);
        return request;
    }
}