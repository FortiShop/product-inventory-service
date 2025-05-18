package org.fortishop.productinventoryservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.productinventoryservice.dto.event.OrderCreatedEvent;
import org.fortishop.productinventoryservice.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryKafkaConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "order.created", groupId = "inventory-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[Kafka] Received order.created: orderId={}, traceId={}", event.getOrderId(), event.getTraceId());
        event.getItems().forEach(item ->
                inventoryService.decreaseStockWithLock(event.getOrderId(), item.getProductId(), item.getQuantity(),
                        event.getTraceId())
        );
    }
}
