package com.eventbooking.dto;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime eventDate,
        Integer totalSeats,
        Integer availableSeats,
        Integer bookedSeats,
        boolean soldOut
) {
}