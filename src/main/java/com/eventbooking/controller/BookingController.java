package com.eventbooking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventbooking.dto.BookingRequest;
import com.eventbooking.dto.BookingResponse;
import com.eventbooking.service.BookingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request
    ) {
        BookingResponse booking =
                bookingService.createBooking(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(booking);
    }

    @GetMapping
    public ResponseEntity<List<BookingResponse>> getAllBookings() {

        return ResponseEntity.ok(
                bookingService.getAllBookings()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                bookingService.getBookingById(id)
        );
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<BookingResponse>> getBookingsByEvent(
            @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(
                bookingService.getBookingsByEvent(eventId)
        );
    }

    @GetMapping("/customer")
    public ResponseEntity<List<BookingResponse>> getBookingsByCustomer(
            @RequestParam String email
    ) {
        return ResponseEntity.ok(
                bookingService.getBookingsByCustomerEmail(email)
        );
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                bookingService.cancelBooking(id)
        );
    }
}