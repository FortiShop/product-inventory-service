package org.fortishop.productinventoryservice.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.productinventoryservice.Repository.InventoryRepository;
import org.fortishop.productinventoryservice.domain.Inventory;
import org.fortishop.productinventoryservice.dto.request.InventoryRequest;
import org.fortishop.productinventoryservice.dto.response.InventoryResponse;
import org.fortishop.productinventoryservice.exception.Product.ProductException;
import org.fortishop.productinventoryservice.exception.Product.ProductExceptionType;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventoryRepository;
    private final RedissonClient redissonClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public InventoryResponse setInventory(Long productId, InventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> inventoryRepository.save(
                        Inventory.builder()
                                .productId(productId)
                                .quantity(0)
                                .build()));

        inventory.setQuantity(request.getQuantity());
        return InventoryResponse.of(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));
        return InventoryResponse.of(inventory);
    }

    @Override
    @Transactional
    public boolean decreaseStockWithLock(Long orderId, Long productId, int quantity, String traceId) {
        String lockKey = "lock:product:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean success = false;

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) {
                Inventory inventory = inventoryRepository.findByProductId(productId)
                        .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

                if (inventory.getQuantity() < quantity) {
                    sendInventoryFailed(orderId, productId, "재고 부족", traceId);
                    return false;
                }

                inventory.adjust(-quantity);
                sendInventoryReserved(orderId, productId, traceId);
                success = true;
            } else {
                sendInventoryFailed(orderId, productId, "락 획득 실패", traceId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendInventoryFailed(orderId, productId, "락 획득 중 인터럽트", traceId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return success;
    }

    @Override
    @Transactional
    public void restoreStock(Long orderId, Long productId, int quantity, String traceId) {
        String lockKey = "lock:product:" + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) {
                Inventory inventory = inventoryRepository.findByProductId(productId)
                        .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));
                inventory.adjust(quantity);
            } else {
                sendInventoryFailed(orderId, productId, "락 획득 실패", traceId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendInventoryFailed(orderId, productId, "락 획득 중 인터럽트", traceId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void sendInventoryReserved(Long orderId, Long productId, String traceId) {
        Map<String, Object> event = Map.of(
                "orderId", orderId,
                "productId", productId,
                "reserved", true,
                "timestamp", LocalDateTime.now().toString(),
                "traceId", traceId
        );
        kafkaTemplate.send("inventory.reserved", orderId.toString(), event);
    }

    private void sendInventoryFailed(Long orderId, Long productId, String reason, String traceId) {
        Map<String, Object> event = Map.of(
                "orderId", orderId,
                "productId", productId,
                "reason", reason,
                "timestamp", LocalDateTime.now().toString(),
                "traceId", traceId
        );
        kafkaTemplate.send("inventory.failed", orderId.toString(), event);
    }
}
