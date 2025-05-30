package org.fortishop.productinventoryservice.dto.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemInfo {
    private Long productId;
    private int quantity;
    private BigDecimal price;
}
