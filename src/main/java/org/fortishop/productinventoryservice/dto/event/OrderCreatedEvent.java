package org.fortishop.productinventoryservice.dto.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {
    private Long orderId;
    private Long memberId;
    private BigDecimal totalPrice;
    private String address;
    private List<OrderItemInfo> items;
    private String createdAt;
    private String traceId;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemInfo {
        private Long productId;
        private int quantity;
        private BigDecimal price;
    }
}
