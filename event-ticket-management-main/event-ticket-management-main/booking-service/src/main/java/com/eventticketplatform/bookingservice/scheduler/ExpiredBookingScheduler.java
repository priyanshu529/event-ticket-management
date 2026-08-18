package com.eventticketplatform.bookingservice.scheduler;

import com.eventticketplatform.bookingservice.client.EventServiceClient;
import com.eventticketplatform.bookingservice.entity.Booking;
import com.eventticketplatform.bookingservice.entity.BookingStatus;
import com.eventticketplatform.bookingservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredBookingScheduler {

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;

    /**
     * Periodically inspects and cleans up uncompleted / abandoned bookings whose
     * 10-minute payment window has elapsed. Restores the deducted seats back to the
     * event's available pool for other attendees.
     */
    @Scheduled(fixedRate = 15000)
    @Transactional
    public void cleanupExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> expiredBookings = bookingRepository.findByStatusAndExpiresAtBefore(
                BookingStatus.PENDING_PAYMENT,
                now
        );

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("Found {} expired booking hold(s). Restoring seats to event pool...", expiredBookings.size());

        for (Booking booking : expiredBookings) {
            try {
                booking.setStatus(BookingStatus.EXPIRED);
                bookingRepository.save(booking);

                // Restore seats back into event inventory
                eventServiceClient.updateSeats(booking.getEventId(), booking.getQuantity());
                log.info("Successfully released {} seat(s) for expired booking ID: {}", 
                        booking.getQuantity(), booking.getId());
            } catch (Exception ex) {
                log.error("Failed to release seats for expired booking ID: {}", booking.getId(), ex);
            }
        }
    }
}
