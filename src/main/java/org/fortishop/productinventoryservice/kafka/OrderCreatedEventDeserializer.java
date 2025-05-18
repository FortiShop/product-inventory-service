package org.fortishop.productinventoryservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.fortishop.productinventoryservice.dto.event.OrderCreatedEvent;

public class OrderCreatedEventDeserializer implements Deserializer<OrderCreatedEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public OrderCreatedEvent deserialize(String topic, byte[] data) {
        try {
            return objectMapper.readValue(data, OrderCreatedEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize OrderCreatedEvent", e);
        }
    }

    @Override
    public void close() {
    }
}
