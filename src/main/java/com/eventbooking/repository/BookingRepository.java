package com.eventbooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventbooking.model.Booking;
import com.eventbooking.model.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByOrderByBookedAtDesc();

    List<Booking> findByEventIdOrderByBookedAtDesc(Long eventId);

    List<Booking> findByCustomerEmailIgnoreCaseOrderByBookedAtDesc(
            String customerEmail
    );

    boolean existsByEventIdAndStatus(
            Long eventId,
            BookingStatus status
    );
}