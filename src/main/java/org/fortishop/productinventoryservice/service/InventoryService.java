package org.fortishop.productinventoryservice.service;

import org.fortishop.productinventoryservice.dto.request.InventoryRequest;
import org.fortishop.productinventoryservice.dto.response.InventoryResponse;

public interface InventoryService {

    InventoryResponse setInventory(Long productId, InventoryRequest request);

    InventoryResponse getInventory(Long productId);

    boolean decreaseStockWithLock(Long orderId, Long productId, int quantity, String traceId);

    void restoreStock(Long orderId, Long productId, int quantity, String traceId);
}
