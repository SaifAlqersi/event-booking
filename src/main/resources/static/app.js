const API = {
    events: "/api/events",
    bookings: "/api/bookings"
};

let allEvents = [];
let selectedEvent = null;


document.addEventListener("DOMContentLoaded", () => {
    loadEvents();

    const dateInput = document.getElementById("eventDate");
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    dateInput.min = now.toISOString().slice(0, 16);
});


async function loadEvents() {
    const loading = document.getElementById("eventsLoading");
    const grid = document.getElementById("eventsGrid");

    loading.classList.remove("hidden");
    grid.innerHTML = "";

    try {
        const response = await fetch(`${API.events}/upcoming`);

        if (!response.ok) {
            throw new Error("Unable to load events.");
        }

        allEvents = await response.json();

        allEvents.sort(
            (a, b) => new Date(a.eventDate) - new Date(b.eventDate)
        );

        renderEvents(allEvents);

    } catch (error) {
        showToast(error.message, true);

    } finally {
        loading.classList.add("hidden");
    }
}


function renderEvents(events) {
    const grid = document.getElementById("eventsGrid");
    const empty = document.getElementById("eventsEmpty");
    const count = document.getElementById("eventCount");

    grid.innerHTML = "";
    count.textContent = events.length;

    if (events.length === 0) {
        empty.classList.remove("hidden");
        return;
    }

    empty.classList.add("hidden");

    events.forEach(event => {

        const date = new Date(event.eventDate);

        const day = date.toLocaleDateString("en-AU", {
            day: "2-digit"
        });

        const month = date.toLocaleDateString("en-AU", {
            month: "short"
        });

        const fullDate = date.toLocaleDateString("en-AU", {
            weekday: "short",
            day: "numeric",
            month: "short",
            year: "numeric"
        });

        const time = date.toLocaleTimeString("en-AU", {
            hour: "2-digit",
            minute: "2-digit"
        });

        const card = document.createElement("article");
        card.className = "event-card";

        card.innerHTML = `
            <div class="event-cover">

                <span class="event-location-badge">
                    ${escapeHtml(event.location)}
                </span>

                <div class="event-date-large">
                    <strong>${day}</strong>
                    <span>${month}</span>
                </div>

            </div>

            <div class="event-body">

                <h3>${escapeHtml(event.title)}</h3>

                <p class="event-description">
                    ${escapeHtml(event.description || "Discover this upcoming event and reserve your place.")}
                </p>

                <div class="event-meta">
                    <span>📅 ${fullDate} · ${time}</span>
                    <span>📍 ${escapeHtml(event.location)}</span>
                </div>

                <div class="seat-row">

                    <div class="seats">
                        <strong>${event.availableSeats} seats available</strong>
                        <span>${event.bookedSeats} of ${event.totalSeats} booked</span>
                    </div>

                    <button
                        class="book-button"
                        ${event.soldOut ? "disabled" : ""}
                        onclick="openBookingModal(${event.id})">

                        ${event.soldOut ? "Sold Out" : "Book Now"}

                    </button>

                </div>

            </div>
        `;

        grid.appendChild(card);
    });
}


function filterEvents() {
    const search =
        document.getElementById("searchInput")
            .value
            .trim()
            .toLowerCase();

    const location =
        document.getElementById("locationFilter").value;

    const filtered = allEvents.filter(event => {

        const matchesSearch =
            event.title.toLowerCase().includes(search) ||
            (event.description || "").toLowerCase().includes(search) ||
            event.location.toLowerCase().includes(search);

        const matchesLocation =
            !location ||
            event.location.toLowerCase().includes(location.toLowerCase());

        return matchesSearch && matchesLocation;
    });

    renderEvents(filtered);
}


function openBookingModal(eventId) {
    selectedEvent = allEvents.find(event => event.id === eventId);

    if (!selectedEvent) {
        showToast("Event could not be found.", true);
        return;
    }

    const date = new Date(selectedEvent.eventDate);

    document.getElementById("bookingEventId").value =
        selectedEvent.id;

    document.getElementById("bookingEventName").textContent =
        selectedEvent.title;

    document.getElementById("bookingEventDate").textContent =
        date.toLocaleDateString("en-AU", {
            day: "numeric",
            month: "short",
            year: "numeric"
        });

    document.getElementById("bookingEventLocation").textContent =
        selectedEvent.location;

    document.getElementById("bookingEventSeats").textContent =
        `${selectedEvent.availableSeats} seats`;

    const quantity = document.getElementById("ticketQuantity");

    quantity.value = 1;
    quantity.max = selectedEvent.availableSeats;

    document.getElementById("bookingModal")
        .classList.remove("hidden");

    document.body.style.overflow = "hidden";
}


function closeBookingModal() {
    document.getElementById("bookingModal")
        .classList.add("hidden");

    document.getElementById("bookingForm").reset();

    selectedEvent = null;
    document.body.style.overflow = "";
}


async function submitBooking(event) {
    event.preventDefault();

    const button = document.getElementById("bookingSubmit");

    const request = {
        eventId: Number(
            document.getElementById("bookingEventId").value
        ),

        customerName:
            document.getElementById("customerName")
                .value
                .trim(),

        customerEmail:
            document.getElementById("customerEmail")
                .value
                .trim(),

        ticketQuantity:
            Number(
                document.getElementById("ticketQuantity").value
            )
    };

    if (
        selectedEvent &&
        request.ticketQuantity > selectedEvent.availableSeats
    ) {
        showToast(
            `Only ${selectedEvent.availableSeats} seats are available.`,
            true
        );

        return;
    }

    setButtonLoading(button, true, "Creating booking...");

    try {

        const response = await fetch(API.bookings, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw new Error(
                await extractError(response)
            );
        }

        const booking = await response.json();

        closeBookingModal();

        showSuccess(
            "Booking confirmed!",
            `${booking.ticketQuantity} ticket(s) reserved for ${booking.eventTitle}. Booking #${booking.id}.`
        );

        await loadEvents();

    } catch (error) {

        showToast(error.message, true);

    } finally {

        setButtonLoading(button, false, "Confirm Booking");
    }
}


function openEventModal() {
    document.getElementById("eventModal")
        .classList.remove("hidden");

    document.body.style.overflow = "hidden";
}


function closeEventModal() {
    document.getElementById("eventModal")
        .classList.add("hidden");

    document.getElementById("eventForm").reset();

    document.body.style.overflow = "";
}


async function submitEvent(event) {
    event.preventDefault();

    const button = document.getElementById("eventSubmit");

    const request = {
        title:
            document.getElementById("eventTitle")
                .value
                .trim(),

        description:
            document.getElementById("eventDescription")
                .value
                .trim(),

        location:
            document.getElementById("eventLocation")
                .value
                .trim(),

        eventDate:
            document.getElementById("eventDate").value,

        totalSeats:
            Number(
                document.getElementById("eventSeats").value
            )
    };

    setButtonLoading(button, true, "Creating event...");

    try {

        const response = await fetch(API.events, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw new Error(
                await extractError(response)
            );
        }

        const created = await response.json();

        closeEventModal();

        showSuccess(
            "Event created!",
            `${created.title} has been published successfully.`
        );

        await loadEvents();

    } catch (error) {

        showToast(error.message, true);

    } finally {

        setButtonLoading(button, false, "Create Event");
    }
}


function showHome() {
    document.getElementById("homePage")
        .classList.remove("hidden");

    document.getElementById("bookingsPage")
        .classList.add("hidden");

    document.getElementById("homeNav")
        .classList.add("active");

    document.getElementById("bookingsNav")
        .classList.remove("active");

    loadEvents();

    window.scrollTo({
        top: 0,
        behavior: "smooth"
    });
}


function showBookings() {
    document.getElementById("homePage")
        .classList.add("hidden");

    document.getElementById("bookingsPage")
        .classList.remove("hidden");

    document.getElementById("homeNav")
        .classList.remove("active");

    document.getElementById("bookingsNav")
        .classList.add("active");

    window.scrollTo({
        top: 0,
        behavior: "smooth"
    });
}


async function searchBookings() {
    const email =
        document.getElementById("bookingEmail")
            .value
            .trim();

    if (!email) {
        showToast("Enter your booking email first.", true);
        return;
    }

    const grid = document.getElementById("bookingsGrid");
    const welcome = document.getElementById("bookingsWelcome");

    welcome.classList.add("hidden");

    grid.innerHTML = `
        <div class="state-box">
            <div class="loader"></div>
            <p>Finding your bookings...</p>
        </div>
    `;

    try {

        const response = await fetch(
            `${API.bookings}/customer?email=${encodeURIComponent(email)}`
        );

        if (!response.ok) {
            throw new Error(
                await extractError(response)
            );
        }

        const bookings = await response.json();

        renderBookings(bookings);

    } catch (error) {

        grid.innerHTML = "";

        showToast(error.message, true);
    }
}


function renderBookings(bookings) {
    const grid = document.getElementById("bookingsGrid");

    grid.innerHTML = "";

    if (bookings.length === 0) {

        grid.innerHTML = `
            <div class="state-box bookings-state">
                <div class="empty-icon">⌕</div>
                <h3>No bookings found</h3>
                <p>There are no reservations associated with this email.</p>
            </div>
        `;

        return;
    }

    bookings.forEach(booking => {

        const status =
            String(booking.status).toUpperCase();

        const cancelled =
            status === "CANCELLED";

        const bookedDate =
            booking.bookedAt
                ? new Date(booking.bookedAt)
                    .toLocaleString("en-AU")
                : "—";

        const card = document.createElement("article");

        card.className = "booking-card";

        card.innerHTML = `
            <div>
                <h3>${escapeHtml(booking.eventTitle)}</h3>
                <p>Booking #${booking.id}</p>
            </div>

            <div class="booking-info">
                <strong>${booking.ticketQuantity} ticket(s)</strong>
                <span>${bookedDate}</span>
            </div>

            <div>
                <span class="status ${cancelled
                    ? "status-cancelled"
                    : "status-confirmed"}">

                    ${escapeHtml(status)}

                </span>
            </div>

            <div>
                ${cancelled
                    ? ""
                    : `<button
                           class="cancel-button"
                           onclick="cancelBooking(${booking.id})">
                           Cancel
                       </button>`
                }
            </div>
        `;

        grid.appendChild(card);
    });
}


async function cancelBooking(bookingId) {
    const confirmed = confirm(
        "Are you sure you want to cancel this booking?"
    );

    if (!confirmed) {
        return;
    }

    try {

        const response = await fetch(
            `${API.bookings}/${bookingId}/cancel`,
            {
                method: "PATCH"
            }
        );

        if (!response.ok) {
            throw new Error(
                await extractError(response)
            );
        }

        showToast("Booking cancelled successfully.");

        await searchBookings();
        await loadEvents();

    } catch (error) {

        showToast(error.message, true);
    }
}


function showSuccess(title, message) {
    document.getElementById("successTitle")
        .textContent = title;

    document.getElementById("successMessage")
        .textContent = message;

    document.getElementById("successModal")
        .classList.remove("hidden");

    document.body.style.overflow = "hidden";
}


function closeSuccessModal() {
    document.getElementById("successModal")
        .classList.add("hidden");

    document.body.style.overflow = "";
}


function showToast(message, isError = false) {
    const toast = document.getElementById("toast");

    toast.textContent = message;

    toast.classList.toggle("error", isError);
    toast.classList.add("show");

    clearTimeout(window.toastTimer);

    window.toastTimer = setTimeout(() => {
        toast.classList.remove("show");
    }, 3500);
}


function setButtonLoading(button, loading, text) {
    button.disabled = loading;
    button.textContent = text;
}


async function extractError(response) {
    try {
        const data = await response.json();

        if (data.message) {
            return data.message;
        }

        if (data.validationErrors) {
            return Object.values(data.validationErrors).join(", ");
        }

    } catch (_) {
    }

    return `Request failed (${response.status}).`;
}


function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}