package org.fortishop.productinventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    private Integer quantity;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Builder
    public Inventory(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.lastUpdated = LocalDateTime.now();
    }

    public void adjust(int delta) {
        this.quantity += delta;
        this.lastUpdated = LocalDateTime.now();
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.lastUpdated = LocalDateTime.now();
    }
}

