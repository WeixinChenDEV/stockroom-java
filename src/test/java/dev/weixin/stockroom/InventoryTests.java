package dev.weixin.stockroom;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static dev.weixin.stockroom.InventoryService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "stockroom.demo=false"
})
@AutoConfigureMockMvc
class InventoryTests {
    @Autowired MockMvc mvc;
    @Autowired InventoryService inventory;
    @Autowired ProductRepository products;
    @Autowired MovementRepository movements;

    @BeforeEach void clean() { movements.deleteAll(); products.deleteAll(); }
    ProductView create() {
        return inventory.save(null, new ProductInput("nb-01", "Notebook", "Stationery", new BigDecimal("5.50"), 5));
    }
    void stock(Long id, Direction direction, int quantity) {
        inventory.move(id, new MovementInput(direction, quantity, "Test delivery / order"));
    }

    @Test void createsProductWithZeroStockAndNormalizesSku() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
            {"sku":"nb-01","name":" Notebook ","category":"Stationery","unitPrice":5.50,"reorderLevel":5}
            """))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.sku").value("NB-01")).andExpect(jsonPath("$.name").value("Notebook"))
            .andExpect(jsonPath("$.quantity").value(0)).andExpect(jsonPath("$.lowStock").value(true));
    }
    @Test void rejectsDuplicateSkuIgnoringCase() throws Exception {
        create();
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
            {"sku":"NB-01","name":"Other","category":"Desk","unitPrice":1,"reorderLevel":1}
            """))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("That SKU already exists."));
    }
    @Test void validatesBlankFieldsNegativePriceAndReorderLevel() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
            {"sku":"invalid sku","name":" ","category":"","unitPrice":-1,"reorderLevel":-2}
            """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        assertThat(products.count()).isZero();
    }
    @Test void rejectsMoreThanTwoDecimalPlaces() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
            {"sku":"A","name":"Pen","category":"Desk","unitPrice":1.235,"reorderLevel":0}
            """))
            .andExpect(status().isBadRequest());
    }
    @Test void updatesMetadataWithoutChangingStock() throws Exception {
        var p = create(); stock(p.id(), Direction.IN, 8);
        mvc.perform(put("/api/products/" + p.id()).contentType(MediaType.APPLICATION_JSON).content("""
            {"sku":"NB-02","name":"New notebook","category":"Desk","unitPrice":6,"reorderLevel":8,"quantity":999}
            """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(8))
            .andExpect(jsonPath("$.lowStock").value(true));
    }
    @Test void recordsIncomingAndOutgoingStockWithBalances() throws Exception {
        var p = create(); stock(p.id(), Direction.IN, 10);
        mvc.perform(post("/api/products/" + p.id() + "/movements").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"direction":"OUT","quantity":3,"note":"Customer order"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(7));
        mvc.perform(get("/api/movements")).andExpect(jsonPath("$[0].balanceAfter").value(7))
            .andExpect(jsonPath("$[0].note").value("Customer order"));
        assertThat(movements.count()).isEqualTo(2);
    }
    @Test void rejectsOversellingWithoutAddingMovement() throws Exception {
        var p = create(); stock(p.id(), Direction.IN, 2);
        mvc.perform(post("/api/products/" + p.id() + "/movements").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"direction":"OUT","quantity":3,"note":"Too many"}
                """))
            .andExpect(status().isConflict());
        assertThat(inventory.list().getFirst().quantity()).isEqualTo(2);
        assertThat(movements.count()).isEqualTo(1);
    }
    @Test void rejectsZeroFractionalMovementAndUnknownDirection() throws Exception {
        var p = create();
        for (String body : new String[]{"{\"direction\":\"IN\",\"quantity\":0,\"note\":\"Bad\"}",
                "{\"direction\":\"SIDEWAYS\",\"quantity\":1,\"note\":\"Bad\"}",
                "{\"direction\":\"IN\",\"quantity\":1.5,\"note\":\"Bad\"}"}) {
            mvc.perform(post("/api/products/" + p.id() + "/movements").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(movements.count()).isZero();
    }
    @Test void returnsNotFoundForUnknownProduct() throws Exception {
        mvc.perform(post("/api/products/999999/movements").contentType(MediaType.APPLICATION_JSON)
            .content("{\"direction\":\"IN\",\"quantity\":1,\"note\":\"Delivery\"}"))
            .andExpect(status().isNotFound());
    }
    @Test void calculatesValueAndIncludesReorderBoundary() throws Exception {
        var p = create(); stock(p.id(), Direction.IN, 5);
        mvc.perform(get("/api/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.products").value(1)).andExpect(jsonPath("$.units").value(5))
            .andExpect(jsonPath("$.lowStock").value(1)).andExpect(jsonPath("$.stockValue").value(27.50));
    }
    @Test void exportsEscapedCsvAndNeutralizesFormulaPrefixes() throws Exception {
        var p = inventory.save(null, new ProductInput("CSV-1", "=SUM(1,2)\n\"quoted\"", "Desk, paper", new BigDecimal("1.25"), 0));
        stock(p.id(), Direction.IN, 2);
        mvc.perform(get("/api/export")).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=stockroom.csv"));
        assertThat(inventory.csv()).contains("\"'=SUM(1,2)\n\"\"quoted\"\"\"", "\"Desk, paper\"", "1.25,2,0,2.50\r\n");
    }
    @Test void preventsTwoConcurrentWithdrawalsFromOverselling() throws Exception {
        var p = create(); stock(p.id(), Direction.IN, 1);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> withdraw = () -> {
                start.await();
                try { stock(p.id(), Direction.OUT, 1); return true; }
                catch (ResponseStatusException error) {
                    assertThat(error.getStatusCode().value()).isEqualTo(409);
                    return false;
                }
            };
            var first = pool.submit(withdraw); var second = pool.submit(withdraw); start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(inventory.list().getFirst().quantity()).isZero();
        assertThat(movements.count()).isEqualTo(2);
    }
}
