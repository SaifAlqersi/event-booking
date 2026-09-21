package com.eventbooking.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String title;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String location;

    @NotNull
    @FutureOrPresent
    @Column(nullable = false)
    private LocalDateTime eventDate;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer totalSeats;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer availableSeats;

    public Event() {
    }

    public Event(
            String title,
            String description,
            String location,
            LocalDateTime eventDate,
            Integer totalSeats
    ) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.eventDate = eventDate;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
    }

    @PrePersist
    public void initializeAvailableSeats() {
        if (availableSeats == null && totalSeats != null) {
            availableSeats = totalSeats;
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public Integer getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(Integer totalSeats) {
        this.totalSeats = totalSeats;
    }

    public Integer getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(Integer availableSeats) {
        this.availableSeats = availableSeats;
    }
}