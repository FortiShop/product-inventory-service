package org.fortishop.productinventoryservice.service;

import lombok.RequiredArgsConstructor;
import org.fortishop.productinventoryservice.Repository.InventoryRepository;
import org.fortishop.productinventoryservice.domain.Inventory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestInventoryHelper {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public Inventory findByProductIdWithLock(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("not found"));
    }
}
