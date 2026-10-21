package dev.weixin.stockroom;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    Product product;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    InventoryService.Direction direction;
    int quantity;
    int balanceAfter;
    @Column(nullable = false, length = 160)
    String note;
    @Column(nullable = false)
    Instant recordedAt;
    protected StockMovement() {}
}
