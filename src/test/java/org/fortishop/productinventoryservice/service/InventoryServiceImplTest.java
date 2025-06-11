package org.fortishop.productinventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.fortishop.productinventoryservice.Repository.InventoryRepository;
import org.fortishop.productinventoryservice.domain.Inventory;
import org.fortishop.productinventoryservice.dto.request.InventoryRequest;
import org.fortishop.productinventoryservice.dto.response.InventoryResponse;
import org.fortishop.productinventoryservice.exception.Product.ProductException;
import org.fortishop.productinventoryservice.kafka.InventoryEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @Mock
    private ProductSyncService productSyncService;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private InventoryEventProducer inventoryEventProducer;

    @Mock
    private RLock lock;

    @Test
    @DisplayName("기존 재고 수정")
    void setInventory_updateExisting() {
        Long productId = 1L;
        InventoryRequest request = new InventoryRequest(30);
        Inventory inventory = Inventory.builder().productId(productId).quantity(10).build();

        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        InventoryResponse response = inventoryService.setInventory(productId, request);

        assertThat(response.getQuantity()).isEqualTo(30);
        verify(productSyncService).updateQuantity(productId, 30);
    }

    @Test
    @DisplayName("재고 조회 성공")
    void getInventory_success() {
        Long productId = 1L;
        Inventory inventory = Inventory.builder().productId(productId).quantity(20).build();

        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        InventoryResponse response = inventoryService.getInventory(productId);

        assertThat(response.getQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("재고 조회 실패 - 존재하지 않음")
    void getInventory_notFound() {
        Long productId = 1L;
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInventory(productId))
                .isInstanceOf(ProductException.class);
    }

    @Test
    @DisplayName("재고 차감 성공 및 이벤트 발행")
    void decreaseStock_success() throws Exception {
        Long orderId = 100L;
        Long productId = 1L;
        String traceId = "trace123";
        Inventory inventory = Inventory.builder().productId(productId).quantity(50).build();

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(eq(5L), eq(TimeUnit.SECONDS))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        boolean result = inventoryService.decreaseStockWithLock(orderId, productId, 20, traceId);

        assertThat(result).isTrue();
        assertThat(inventory.getQuantity()).isEqualTo(30);

        verify(inventoryEventProducer).sendInventoryReserved(eq(orderId), eq(productId), eq(traceId));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("재고 차감 실패 - 수량 부족")
    void decreaseStock_insufficientQuantity() throws Exception {
        Long orderId = 200L;
        Long productId = 1L;
        String traceId = "trace456";
        Inventory inventory = Inventory.builder().productId(productId).quantity(10).build();

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(eq(5L), eq(TimeUnit.SECONDS))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        boolean result = inventoryService.decreaseStockWithLock(orderId, productId, 20, traceId);

        assertThat(result).isFalse();
        verify(inventoryEventProducer).sendInventoryFailed(eq(orderId), eq(productId), eq("재고 부족"), eq(traceId));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("재고 차감 실패 - 락 획득 실패")
    void decreaseStock_lockFail() throws Exception {
        Long orderId = 300L;
        Long productId = 1L;
        String traceId = "trace789";

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(eq(5L), eq(TimeUnit.SECONDS))).willReturn(false);

        boolean result = inventoryService.decreaseStockWithLock(orderId, productId, 10, traceId);

        assertThat(result).isFalse();
        verify(inventoryRepository, never()).findByProductId(any());
        verify(inventoryEventProducer).sendInventoryFailed(eq(orderId), eq(productId), eq("락 획득 실패"), eq(traceId));
    }
}
