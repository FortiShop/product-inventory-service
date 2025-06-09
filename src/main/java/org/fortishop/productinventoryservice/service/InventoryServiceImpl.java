package org.fortishop.productinventoryservice.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.productinventoryservice.Repository.InventoryRepository;
import org.fortishop.productinventoryservice.domain.Inventory;
import org.fortishop.productinventoryservice.dto.request.InventoryRequest;
import org.fortishop.productinventoryservice.dto.response.InventoryResponse;
import org.fortishop.productinventoryservice.exception.Product.ProductException;
import org.fortishop.productinventoryservice.exception.Product.ProductExceptionType;
import org.fortishop.productinventoryservice.kafka.InventoryEventProducer;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final RedissonClient redissonClient;
    private final InventoryEventProducer inventoryEventProducer;
    private final ProductSyncService productSyncService;

    @Override
    @Transactional
    public InventoryResponse setInventory(Long productId, InventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

        inventory.setQuantity(request.getQuantity());
        productSyncService.updateQuantity(productId, request.getQuantity());
        return InventoryResponse.of(inventory);
    }

    @Override
    @Transactional
    public InventoryResponse getInventory(Long productId) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
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
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    log.info("🔐 [Thread: {}] RedissonLock key={}, isLocked={}, heldByCurrentThread={}",
                            Thread.currentThread().getName(),
                            lockKey,
                            lock.isLocked(),
                            lock.isHeldByCurrentThread());

                    Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                            .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

                    if (inventory.getQuantity() < quantity) {
                        inventoryEventProducer.sendInventoryFailed(orderId, productId, "재고 부족", traceId);
                        return false;
                    }

                    inventory.adjust(-quantity);
                    inventoryRepository.save(inventory);
                    log.info("✅ 재고 차감 성공 직후 수량: productId={}, 남은 수량={}", productId, inventory.getQuantity());

                    inventoryEventProducer.sendInventoryReserved(orderId, productId, traceId);
                    success = true;

                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.warn("❌ 락 획득 실패: productId={}, orderId={}", productId, orderId);
                inventoryEventProducer.sendInventoryFailed(orderId, productId, "락 획득 실패", traceId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inventoryEventProducer.sendInventoryFailed(orderId, productId, "락 획득 중 인터럽트", traceId);
        } catch (Exception e) {
            log.error("❌ 재고 차감 중 예외 발생: orderId={}, error={}", orderId, e.getMessage());
            inventoryEventProducer.sendInventoryFailed(orderId, productId, "예외 발생: " + e.getMessage(), traceId);
        }
        return success;
    }

    @Override
    @Transactional
    public void restoreStock(Long orderId, Long productId, int quantity, String traceId) {
        String lockKey = "lock:product:" + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                            .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

                    inventory.adjust(quantity);
                    inventoryRepository.save(inventory);
                    log.info("✅ 재고 복원 성공: productId={}, 복원 수량={}, 현재 수량={}",
                            productId, quantity, inventory.getQuantity());

                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }

            } else {
                log.warn("❌ 재고 복원 실패 (락 획득 실패): productId={}, orderId={}", productId, orderId);
                inventoryEventProducer.sendInventoryFailed(orderId, productId, "락 획득 실패", traceId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inventoryEventProducer.sendInventoryFailed(orderId, productId, "락 획득 중 인터럽트", traceId);
        } catch (Exception e) {
            log.error("❌ 재고 복원 중 예외 발생: orderId={}, error={}", orderId, e.getMessage());
            inventoryEventProducer.sendInventoryFailed(orderId, productId, "예외 발생: " + e.getMessage(), traceId);
        }
    }
}
