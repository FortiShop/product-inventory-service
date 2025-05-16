package org.fortishop.productinventoryservice.service;

import org.fortishop.productinventoryservice.request.InventoryRequest;
import org.fortishop.productinventoryservice.response.InventoryResponse;

public interface InventoryService {
    
    InventoryResponse setInventory(Long productId, InventoryRequest request);

    InventoryResponse getInventory(Long productId);
}
