package org.fortishop.productinventoryservice.dto.response;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fortishop.productinventoryservice.domain.Inventory;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@AllArgsConstructor
public class InventoryResponse {
    private Long productId;
    private Integer quantity;
    private LocalDateTime lastUpdated;

    public static InventoryResponse of(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getLastUpdated()
        );
    }
}
