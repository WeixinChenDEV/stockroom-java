package dev.weixin.stockroom;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import static dev.weixin.stockroom.InventoryService.*;

@RestController
@RequestMapping("/api")
class InventoryController {
    private final InventoryService inventory;
    InventoryController(InventoryService inventory) { this.inventory = inventory; }
    @GetMapping("/products") List<ProductView> products() { return inventory.list(); }
    @GetMapping("/products/{id}") ProductView product(@PathVariable Long id) { return inventory.get(id); }
    @GetMapping("/summary") Summary summary() { return inventory.summary(); }
    @GetMapping("/movements") List<MovementView> history() { return inventory.history(); }
    @PostMapping("/products") ResponseEntity<ProductView> create(@Valid @RequestBody ProductInput input) {
        var product = inventory.save(null, input);
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(product);
    }
    @PutMapping("/products/{id}") ProductView update(@PathVariable Long id, @Valid @RequestBody ProductInput input) {
        return inventory.save(id, input);
    }
    @PostMapping("/products/{id}/movements") ProductView move(@PathVariable Long id, @Valid @RequestBody MovementInput input) {
        return inventory.move(id, input);
    }
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8") ResponseEntity<String> export() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stockroom.csv")
            .body(inventory.csv());
    }
}
