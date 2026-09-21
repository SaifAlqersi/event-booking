package com.eventbooking.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_booking_event_id", columnList = "event_id"),
                @Index(name = "idx_booking_customer_email", columnList = "customer_email"),
                @Index(name = "idx_booking_status", columnList = "status")
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 180)
    private String customerEmail;

    @Column(name = "ticket_quantity", nullable = false)
    private Integer ticketQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "booked_at", nullable = false, updatable = false)
    private LocalDateTime bookedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public Booking() {
    }

    public Booking(
            Event event,
            String customerName,
            String customerEmail,
            Integer ticketQuantity
    ) {
        this.event = event;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.ticketQuantity = ticketQuantity;
        this.status = BookingStatus.CONFIRMED;
        this.bookedAt = LocalDateTime.now();
    }

    @PrePersist
    public void initializeBooking() {
        if (status == null) {
            status = BookingStatus.CONFIRMED;
        }

        if (bookedAt == null) {
            bookedAt = LocalDateTime.now();
        }
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public Integer getTicketQuantity() {
        return ticketQuantity;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getBookedAt() {
        return bookedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
}