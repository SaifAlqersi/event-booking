package com.eventbooking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventbooking.dto.EventRequest;
import com.eventbooking.dto.EventResponse;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.model.BookingStatus;
import com.eventbooking.model.Event;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventRepository;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public EventService(
            EventRepository eventRepository,
            BookingRepository bookingRepository
    ) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public EventResponse createEvent(EventRequest request) {

        Event event = new Event(
                request.getTitle().trim(),
                request.getDescription(),
                request.getLocation().trim(),
                request.getEventDate(),
                request.getTotalSeats()
        );

        return toResponse(
                eventRepository.save(event)
        );
    }

    public List<EventResponse> getAllEvents() {

        return eventRepository
                .findAll(Sort.by(Sort.Direction.ASC, "eventDate"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public EventResponse getEventById(Long id) {
        return toResponse(findEvent(id));
    }

    public List<EventResponse> getUpcomingEvents() {

        return eventRepository
                .findByEventDateAfterOrderByEventDateAsc(LocalDateTime.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EventResponse> searchEvents(String title) {

        if (title == null || title.isBlank()) {
            throw new BadRequestException(
                    "search title must not be empty"
            );
        }

        return eventRepository
                .findByTitleContainingIgnoreCase(title.trim())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventResponse updateEvent(
            Long id,
            EventRequest request
    ) {

        Event event = findEvent(id);

        int bookedSeats =
                event.getTotalSeats()
                        - event.getAvailableSeats();

        if (request.getTotalSeats() < bookedSeats) {
            throw new BadRequestException(
                    "total seats cannot be less than already booked seats"
            );
        }

        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation().trim());
        event.setEventDate(request.getEventDate());
        event.setTotalSeats(request.getTotalSeats());
        event.setAvailableSeats(
                request.getTotalSeats() - bookedSeats
        );

        return toResponse(
                eventRepository.save(event)
        );
    }

    @Transactional
    public void deleteEvent(Long id) {

        Event event = findEvent(id);

        boolean hasConfirmedBookings =
                bookingRepository.existsByEventIdAndStatus(
                        id,
                        BookingStatus.CONFIRMED
                );

        if (hasConfirmedBookings) {
            throw new BadRequestException(
                    "event with confirmed bookings cannot be deleted"
            );
        }

        eventRepository.delete(event);
    }

    private Event findEvent(Long id) {

        return eventRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "event not found with id: " + id
                        )
                );
    }

    private EventResponse toResponse(Event event) {

        int bookedSeats =
                event.getTotalSeats()
                        - event.getAvailableSeats();

        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.getEventDate(),
                event.getTotalSeats(),
                event.getAvailableSeats(),
                bookedSeats,
                event.getAvailableSeats() == 0
        );
    }
}