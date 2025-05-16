package org.fortishop.productinventoryservice.dto.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long memberId;
    private Long totalPrice;
    private List<Item> items;
    private String createdAt;
    private String traceId;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long productId;
        private int quantity;
        private BigDecimal price;
    }
}
