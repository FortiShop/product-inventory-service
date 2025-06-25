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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final RedissonClient redissonClient;
    private final InventoryEventProducer inventoryEventProducer;
    private final ProductSyncService productSyncService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String INVENTORY_KEY_PREFIX = "inventory::product::";

    @Override
    @Transactional
    public InventoryResponse setInventory(Long productId, InventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

        inventory.setQuantity(request.getQuantity());
        productSyncService.updateQuantity(productId, request.getQuantity());

        String key = INVENTORY_KEY_PREFIX + productId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.delete(key);
                log.debug("🗑️ 캐시 삭제 완료: {}", key);
            }
        });

        return InventoryResponse.of(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(Long productId) {
        String key = INVENTORY_KEY_PREFIX + productId;
        InventoryResponse cached = (InventoryResponse) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.debug("✅ Redis 캐시 조회 성공: {}", key);
            return cached;
        }

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

        InventoryResponse response = InventoryResponse.of(inventory);
        redisTemplate.opsForValue().set(key, response, 5, TimeUnit.MINUTES);
        log.debug("📦 Redis 캐시 저장: {}", key);

        return response;
    }

    @Transactional
    public boolean decreaseStockWithLock(Long orderId, Long productId, int quantity, String traceId) {
        String lockKey = "lock:product:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean success = false;

        try {
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                log.info("🔐 락 획득: {}", lockKey);

                Inventory inventory = inventoryRepository.findByProductId(productId)
                        .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

                if (inventory.getQuantity() < quantity) {
                    inventoryEventProducer.sendInventoryFailed(orderId, productId, "재고 부족", traceId);
                    return false;
                }

                inventory.adjust(-quantity);
                inventoryRepository.save(inventory);

                inventoryEventProducer.sendInventoryReserved(orderId, productId, traceId);
                success = true;

                // 트랜잭션 커밋 이후에 락 해제
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                            log.info("🔓 트랜잭션 커밋 후 락 해제: {}", lockKey);
                        }
                        redisTemplate.delete(INVENTORY_KEY_PREFIX + productId);
                    }
                });

            } else {
                log.warn("❌ 락 획득 실패: productId={}, orderId={}", productId, orderId);
                inventoryEventProducer.sendInventoryFailed(orderId, productId, "락 획득 실패", traceId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inventoryEventProducer.sendInventoryFailed(orderId, productId, "락 획득 중 인터럽트", traceId);
        } catch (Exception e) {
            log.error("❌ 재고 차감 중 예외 발생: {}", e.getMessage());
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
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                log.info("🔐 락 획득 성공: key={}, orderId={}", lockKey, orderId);

                Inventory inventory = inventoryRepository.findByProductId(productId)
                        .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

                inventory.adjust(quantity);
                inventoryRepository.save(inventory);

                log.info("✅ 재고 복원 성공: productId={}, 복원 수량={}, 현재 수량={}",
                        productId, quantity, inventory.getQuantity());

                // 트랜잭션 커밋 이후에 락 해제
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                            log.info("🔓 트랜잭션 커밋 후 락 해제: {}", lockKey);
                        }
                        redisTemplate.delete(INVENTORY_KEY_PREFIX + productId);
                    }
                });

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
