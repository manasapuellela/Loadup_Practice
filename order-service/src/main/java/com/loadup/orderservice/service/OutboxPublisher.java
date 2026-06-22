package com.loadup.orderservice.service;

import com.loadup.orderservice.entity.OutboxEvent;
import com.loadup.orderservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RestClient restClient;
    private final String notificationServiceUrl;

    // RestClient over WebClient: this poller processes events sequentially by design, so it doesn't need WebClient's reactive, non-blocking semantics.
    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                            @Value("${notification-service.base-url}") String notificationServiceUrl) {
        this.outboxEventRepository = outboxEventRepository;
        this.notificationServiceUrl = notificationServiceUrl;
        this.restClient = RestClient.create();
    }

    // Runs on its own clock, independent of any HTTP request, this is what keeps order creation from ever waiting on notification-service.
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByProcessedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            if (event.getAttemptCount() >= MAX_ATTEMPTS) {
                log.error("Outbox event {} exceeded max attempts ({}), skipping", event.getId(), MAX_ATTEMPTS);
                continue;
                // Skipped, not deleted, the row stays in the table as a record of the failure. 
                // A real production system would route this to a dead-letter table or alert instead of just logging it.
            }
            dispatch(event);
        }
    }

    private void dispatch(OutboxEvent event) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-Id", event.getTenantId());
            // The tenant header is forwarded here too, multi-tenancy doesn't stop at order-service, 
            // notification-service enforces it again independently on its own side.

            restClient.post()
                    .uri(notificationServiceUrl + "/api/notifications/order-events")
                    .headers(h -> h.addAll(headers))
                    .body(event.getPayload())
                    .retrieve()
                    .toBodilessEntity();

            event.markProcessed();
            outboxEventRepository.save(event);
            log.info("Successfully dispatched outbox event {}", event.getId());

        } catch (Exception e) {
            event.incrementAttempt();
            outboxEventRepository.save(event);
            log.warn("Failed to dispatch outbox event {} (attempt {}): {}",
                    event.getId(), event.getAttemptCount(), e.getMessage());
            // Left unprocessed on purpose, the next scheduled run picks this event back up automatically, no manual retry needed.
        }
    }
}