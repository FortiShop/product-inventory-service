package org.fortishop.productinventoryservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.productinventoryservice.dto.event.OrderCreatedEvent;
import org.fortishop.productinventoryservice.dto.event.PaymentFailedEvent;
import org.fortishop.productinventoryservice.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryKafkaConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "order.created", groupId = "inventory-group", containerFactory = "orderCreatedListenerContainerFactory")
    public void handleOrderCreated(OrderCreatedEvent event, Acknowledgment ack) {
        try {
            log.info("[Kafka] Received order.created: orderId={}, traceId={}", event.getOrderId(), event.getTraceId());
            event.getItems().forEach(item ->
                    inventoryService.decreaseStockWithLock(event.getOrderId(), item.getProductId(), item.getQuantity(),
                            event.getTraceId())
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("처리 실패: order.created", e);
            throw e;
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "inventory-group", containerFactory = "paymentFailedListenerContainerFactory")
    public void handleInventoryRestore(PaymentFailedEvent event, Acknowledgment ack) {
        try {
            log.info("[Kafka] Received payment.failed, restore inventory: orderId={}, traceId={}", event.getOrderId(),
                    event.getTraceId());
            event.getItems().forEach(item ->
                    inventoryService.restoreStock(event.getOrderId(), item.getProductId(), item.getQuantity(),
                            event.getTraceId())
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("처리 실패: payment.failed", e);
            throw e;
        }
    }

    @KafkaListener(topics = "order.created.dlq", groupId = "inventory-dlq-group")
    public void handleDlq(OrderCreatedEvent event) {
        log.error("[DLQ 메시지 확인] order.created 처리 실패 : {}", event);
        // slack 또는 이메일로 개발자, 관리자에게 알림
    }

    @KafkaListener(topics = "payment.failed.dlq", groupId = "inventory-dlq-group")
    public void handleDlq(PaymentFailedEvent event) {
        log.error("[DLQ 메시지 확인] payment.failed 처리 실패 : {}", event);
        // slack 또는 이메일로 개발자, 관리자에게 알림
    }
}
