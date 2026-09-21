package com.eventbooking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventbooking.dto.BookingRequest;
import com.eventbooking.dto.BookingResponse;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.model.Booking;
import com.eventbooking.model.BookingStatus;
import com.eventbooking.model.Event;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventRepository;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;

    public BookingService(
            BookingRepository bookingRepository,
            EventRepository eventRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {

        Event event = eventRepository
                .findByIdForUpdate(request.getEventId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "event not found with id: " + request.getEventId()
                        )
                );

        if (event.getEventDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException(
                    "booking is not available for past events"
            );
        }

        if (event.getAvailableSeats() <= 0) {
            throw new BadRequestException(
                    "event is sold out"
            );
        }

        if (request.getTicketQuantity() > event.getAvailableSeats()) {
            throw new BadRequestException(
                    "requested tickets exceed available seats"
            );
        }

        int remainingSeats =
                event.getAvailableSeats() - request.getTicketQuantity();

        event.setAvailableSeats(remainingSeats);

        Booking booking = new Booking(
                event,
                request.getCustomerName().trim(),
                request.getCustomerEmail().trim().toLowerCase(),
                request.getTicketQuantity()
        );

        eventRepository.save(event);

        Booking savedBooking =
                bookingRepository.save(booking);

        return toResponse(savedBooking);
    }

    public BookingResponse getBookingById(Long id) {
        return toResponse(findBooking(id));
    }

    public List<BookingResponse> getAllBookings() {

        return bookingRepository
                .findAllByOrderByBookedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<BookingResponse> getBookingsByEvent(Long eventId) {

        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException(
                    "event not found with id: " + eventId
            );
        }

        return bookingRepository
                .findByEventIdOrderByBookedAtDesc(eventId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<BookingResponse> getBookingsByCustomerEmail(
            String customerEmail
    ) {

        if (customerEmail == null || customerEmail.isBlank()) {
            throw new BadRequestException(
                    "customer email must not be empty"
            );
        }

        return bookingRepository
                .findByCustomerEmailIgnoreCaseOrderByBookedAtDesc(
                        customerEmail.trim()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId) {

        Booking booking = findBooking(bookingId);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException(
                    "booking is already cancelled"
            );
        }

        Event event = eventRepository
                .findByIdForUpdate(booking.getEvent().getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "event associated with booking was not found"
                        )
                );

        if (event.getEventDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException(
                    "past event bookings cannot be cancelled"
            );
        }

        int restoredSeats =
                event.getAvailableSeats() + booking.getTicketQuantity();

        if (restoredSeats > event.getTotalSeats()) {
            throw new IllegalStateException(
                    "seat availability exceeds event capacity"
            );
        }

        event.setAvailableSeats(restoredSeats);
        booking.cancel();

        eventRepository.save(event);

        return toResponse(
                bookingRepository.save(booking)
        );
    }

    private Booking findBooking(Long id) {

        return bookingRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "booking not found with id: " + id
                        )
                );
    }

    private BookingResponse toResponse(Booking booking) {

        return new BookingResponse(
                booking.getId(),
                booking.getEvent().getId(),
                booking.getEvent().getTitle(),
                booking.getCustomerName(),
                booking.getCustomerEmail(),
                booking.getTicketQuantity(),
                booking.getStatus(),
                booking.getBookedAt(),
                booking.getCancelledAt()
        );
    }
}