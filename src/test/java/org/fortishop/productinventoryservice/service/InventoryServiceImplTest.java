package org.fortishop.productinventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.fortishop.productinventoryservice.Repository.InventoryRepository;
import org.fortishop.productinventoryservice.domain.Inventory;
import org.fortishop.productinventoryservice.dto.request.InventoryRequest;
import org.fortishop.productinventoryservice.dto.response.InventoryResponse;
import org.fortishop.productinventoryservice.exception.Product.ProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RLock lock;

    @Test
    @DisplayName("신규 재고 생성")
    void setInventory_newInventory() {
        Long productId = 1L;
        InventoryRequest request = new InventoryRequest(100);

        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.empty());
        given(inventoryRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        InventoryResponse response = inventoryService.setInventory(productId, request);

        assertThat(response.getQuantity()).isEqualTo(100);
        verify(inventoryRepository).save(any());
    }

    @Test
    @DisplayName("기존 재고 수정")
    void setInventory_updateExisting() {
        Long productId = 1L;
        InventoryRequest request = new InventoryRequest(30);
        Inventory inventory = Inventory.builder().productId(productId).quantity(10).build();

        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        InventoryResponse response = inventoryService.setInventory(productId, request);

        assertThat(response.getQuantity()).isEqualTo(30);
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
        Long productId = 1L;
        String traceId = "trace123";
        Inventory inventory = Inventory.builder().productId(productId).quantity(50).build();

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true); // 🔥 핵심 추가
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        boolean result = inventoryService.decreaseStockWithLock(productId, 20, traceId);

        assertThat(result).isTrue();
        assertThat(inventory.getQuantity()).isEqualTo(30);
        verify(kafkaTemplate).send(eq("inventory.reserved"), eq(productId.toString()), any());
        verify(lock).unlock();
    }

    @Test
    @DisplayName("재고 차감 실패 - 수량 부족")
    void decreaseStock_insufficientQuantity() throws Exception {
        Long productId = 1L;
        String traceId = "trace456";
        Inventory inventory = Inventory.builder().productId(productId).quantity(10).build();

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true); // 🔥 추가
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        boolean result = inventoryService.decreaseStockWithLock(productId, 20, traceId);

        assertThat(result).isFalse();
        verify(kafkaTemplate).send(eq("inventory.failed"), eq(productId.toString()), any());
        verify(lock).unlock(); // now this will pass
    }

    @Test
    @DisplayName("재고 차감 실패 - 락 획득 실패")
    void decreaseStock_lockFail() throws Exception {
        Long productId = 1L;
        String traceId = "trace789";

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any())).willReturn(false);

        boolean result = inventoryService.decreaseStockWithLock(productId, 10, traceId);

        assertThat(result).isFalse();
        verify(inventoryRepository, never()).findByProductId(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}

