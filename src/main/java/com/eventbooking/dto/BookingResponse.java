package com.eventbooking.dto;

import java.time.LocalDateTime;

import com.eventbooking.model.BookingStatus;

public record BookingResponse(
        Long id,
        Long eventId,
        String eventTitle,
        String customerName,
        String customerEmail,
        Integer ticketQuantity,
        BookingStatus status,
        LocalDateTime bookedAt,
        LocalDateTime cancelledAt
) {
}