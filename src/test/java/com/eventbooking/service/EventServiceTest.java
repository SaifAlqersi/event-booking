package com.eventbooking.service;

import com.eventbooking.dto.EventRequest;
import com.eventbooking.dto.EventResponse;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
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
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private EventService eventService;

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
    void shouldCreateEvent() {
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventRequest request = request(100);

        EventResponse response = eventService.createEvent(request);

        assertEquals("DevOps Conference", response.title());
        assertEquals(100, response.totalSeats());
        assertEquals(100, response.availableSeats());
        assertEquals(0, response.bookedSeats());

        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void shouldReturnEventById() {
        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        EventResponse response = eventService.getEventById(1L);

        assertEquals("DevOps Conference", response.title());
    }

    @Test
    void shouldThrowWhenEventDoesNotExist() {
        when(eventRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> eventService.getEventById(99L)
        );
    }

    @Test
    void shouldRejectSeatReductionBelowBookedSeats() {
        event.setAvailableSeats(60);

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        EventRequest request = request(30);

        assertThrows(
                BadRequestException.class,
                () -> eventService.updateEvent(1L, request)
        );
    }

    @Test
    void shouldPreventDeletingEventWithConfirmedBookings() {
        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        when(bookingRepository.existsByEventIdAndStatus(
                1L,
                BookingStatus.CONFIRMED
        )).thenReturn(true);

        assertThrows(
                BadRequestException.class,
                () -> eventService.deleteEvent(1L)
        );

        verify(eventRepository, never()).delete(any(Event.class));
    }

    private EventRequest request(int seats) {
        EventRequest request = new EventRequest();
        request.setTitle("DevOps Conference");
        request.setDescription("DevOps event");
        request.setLocation("Melbourne");
        request.setEventDate(LocalDateTime.now().plusDays(10));
        request.setTotalSeats(seats);
        return request;
    }
}