package dev.weixin.stockroom;

import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class InventoryService {
    public enum Direction { IN, OUT }
    public record ProductInput(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,32}") String sku,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 40) String category,
        @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Min(0) @Max(1000000) int reorderLevel) {}
    public record MovementInput(@NotNull Direction direction,
        @Min(1) @Max(1000000) int quantity, @NotBlank @Size(max = 160) String note) {}
    public record ProductView(Long id, String sku, String name, String category,
        BigDecimal unitPrice, int quantity, int reorderLevel, boolean lowStock) {}
    public record MovementView(Long id, String sku, String productName, Direction direction,
        int quantity, int balanceAfter, String note, Instant recordedAt) {}
    public record Summary(int products, long units, long lowStock, BigDecimal stockValue) {}

    private final ProductRepository products;
    private final MovementRepository movements;
    public InventoryService(ProductRepository products, MovementRepository movements) {
        this.products = products;
        this.movements = movements;
    }

    public List<ProductView> list() {
        return products.findAll().stream().sorted(Comparator.comparing(p -> p.name.toLowerCase(Locale.ROOT)))
            .map(this::view).toList();
    }
    public Summary summary() {
        var all = list();
        return new Summary(all.size(), all.stream().mapToLong(ProductView::quantity).sum(),
            all.stream().filter(ProductView::lowStock).count(),
            all.stream().map(p -> p.unitPrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(new BigDecimal("0.00"), BigDecimal::add));
    }
    public ProductView get(Long id) {
        return view(products.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.")));
    }
    @Transactional
    public ProductView save(Long id, ProductInput input) {
        String sku = input.sku().toUpperCase(Locale.ROOT);
        if (products.existsBySkuAndIdNot(sku, id == null ? -1L : id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That SKU already exists.");
        Product product = id == null ? new Product() : require(id);
        product.sku = sku;
        product.name = input.name().strip();
        product.category = input.category().strip();
        product.unitPrice = input.unitPrice().setScale(2);
        product.reorderLevel = input.reorderLevel();
        return view(products.saveAndFlush(product));
    }
    @Transactional
    public ProductView move(Long id, MovementInput input) {
        Product product = products.findForUpdate(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        long balance = (long) product.quantity + (input.direction() == Direction.IN ? input.quantity() : -input.quantity());
        if (balance < 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock for this withdrawal.");
        if (balance > 1000000) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock cannot exceed 1,000,000 units.");
        product.quantity = (int) balance;
        StockMovement movement = new StockMovement();
        movement.product = product;
        movement.direction = input.direction();
        movement.quantity = input.quantity();
        movement.balanceAfter = product.quantity;
        movement.note = input.note().strip();
        movement.recordedAt = Instant.now();
        movements.save(movement);
        return view(product);
    }
    public List<MovementView> history() {
        return movements.findTop50ByOrderByRecordedAtDescIdDesc().stream().map(m ->
            new MovementView(m.id, m.product.sku, m.product.name, m.direction,
                m.quantity, m.balanceAfter, m.note, m.recordedAt)).toList();
    }
    public String csv() {
        StringBuilder csv = new StringBuilder("SKU,Name,Category,Unit price (EUR),Quantity,Reorder level,Stock value (EUR)\r\n");
        for (ProductView p : list()) csv.append(String.join(",", cell(p.sku()), cell(p.name()), cell(p.category()),
            p.unitPrice().toPlainString(), Integer.toString(p.quantity()), Integer.toString(p.reorderLevel()),
            p.unitPrice().multiply(BigDecimal.valueOf(p.quantity())).toPlainString())).append("\r\n");
        return csv.toString();
    }
    private String cell(String value) {
        // Spreadsheet programs may interpret user-supplied text as formulas.
        if (!value.isEmpty() && "=+-@\t\r\n".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    private Product require(Long id) {
        return products.findForUpdate(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }
    private ProductView view(Product p) {
        return new ProductView(p.id, p.sku, p.name, p.category, p.unitPrice, p.quantity,
            p.reorderLevel, p.quantity <= p.reorderLevel);
    }
}
