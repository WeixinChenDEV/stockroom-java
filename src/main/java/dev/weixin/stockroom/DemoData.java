package dev.weixin.stockroom;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import static dev.weixin.stockroom.InventoryService.*;

@Component
@ConditionalOnProperty(name = "stockroom.demo", havingValue = "true")
class DemoData implements CommandLineRunner {
    private final InventoryService inventory;
    DemoData(InventoryService inventory) { this.inventory = inventory; }
    @Override public void run(String... args) {
        if (!inventory.list().isEmpty()) return;
        add("ST-001", "A5 dotted notebook", "Stationery", "5.50", 24, 10);
        add("ST-002", "Black gel pen · pack of 3", "Stationery", "3.20", 6, 8);
        add("DS-001", "Aluminium laptop stand", "Desk essentials", "24.90", 12, 4);
        add("DS-002", "Cork desk mat", "Desk essentials", "14.00", 3, 5);
        add("AC-001", "USB-C cable · 1 metre", "Accessories", "8.50", 18, 6);
        add("AC-002", "Canvas pencil case", "Accessories", "6.00", 0, 4);
    }
    private void add(String sku, String name, String category, String price, int quantity, int reorder) {
        var product = inventory.save(null, new ProductInput(sku, name, category, new BigDecimal(price), reorder));
        if (quantity > 0) inventory.move(product.id(), new MovementInput(Direction.IN, quantity, "Opening demo stock"));
    }
}
