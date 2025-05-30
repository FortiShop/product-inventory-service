package org.fortishop.productinventoryservice.dto.event;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private Long orderId;
    private List<OrderItemInfo> items;
    private String reason;
    private String timestamp;
    private String traceId;
}
