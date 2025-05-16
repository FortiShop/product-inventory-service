package org.fortishop.productinventoryservice.Repository;

import java.util.Optional;
import org.fortishop.productinventoryservice.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);
}

