package org.fortishop.productinventoryservice.kafka;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.productinventoryservice.dto.event.InventoryFailedEvent;
import org.fortishop.productinventoryservice.dto.event.InventoryReservedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendInventoryReserved(Long orderId, Long productId, String traceId) {
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .orderId(orderId)
                .productId(productId)
                .reserved(true)
                .timestamp(LocalDateTime.now().toString())
                .traceId(traceId)
                .build();
        try {
            kafkaTemplate.send("inventory.reserved", orderId.toString(), event);
            log.info("[Kafka] Sent delivery.started: {}", event);
        } catch (Exception e) {
            log.error("[Kafka] Failed to serialize inventory.reserved", e);
        }
    }

    public void sendInventoryFailed(Long orderId, Long productId, String reason, String traceId) {
        InventoryFailedEvent event = InventoryFailedEvent.builder()
                .orderId(orderId)
                .productId(productId)
                .reason(reason)
                .timestamp(LocalDateTime.now().toString())
                .traceId(traceId)
                .build();
        try {
            kafkaTemplate.send("inventory.failed", orderId.toString(), event);
            log.info("[Kafka] Sent delivery.completed: {}", event);
        } catch (Exception e) {
            log.error("[Kafka] Failed to serialize inventory.failed event", e);
        }
    }
}
