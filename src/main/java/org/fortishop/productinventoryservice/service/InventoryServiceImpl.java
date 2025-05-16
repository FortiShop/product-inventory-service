package org.fortishop.productinventoryservice.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
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
    public boolean decreaseStockWithLock(Long productId, int quantity, String traceId) {
        String lockKey = "lock:product:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean success = false;

        try {
            if (lock.tryLock(5, 2, TimeUnit.SECONDS)) {
                Inventory inventory = inventoryRepository.findByProductId(productId)
                        .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

                if (inventory.getQuantity() < quantity) {
                    sendInventoryFailed(productId, "재고 부족", traceId);
                    return false;
                }

                inventory.adjust(-quantity);
                sendInventoryReserved(productId, traceId);
                success = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return success;
    }

    private void sendInventoryReserved(Long productId, String traceId) {
        Map<String, Object> event = Map.of(
                "productId", productId,
                "reserved", true,
                "timestamp", LocalDateTime.now().toString(),
                "traceId", traceId
        );
        kafkaTemplate.send("inventory.reserved", productId.toString(), event);
    }

    private void sendInventoryFailed(Long productId, String reason, String traceId) {
        Map<String, Object> event = Map.of(
                "productId", productId,
                "reason", reason,
                "timestamp", LocalDateTime.now().toString(),
                "traceId", traceId
        );
        kafkaTemplate.send("inventory.failed", productId.toString(), event);
    }

}
