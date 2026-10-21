package dev.weixin.stockroom;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, unique = true, length = 32)
    String sku;
    @Column(nullable = false, length = 80)
    String name;
    @Column(nullable = false, length = 40)
    String category;
    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal unitPrice;
    int quantity;
    int reorderLevel;
    protected Product() {}
}
