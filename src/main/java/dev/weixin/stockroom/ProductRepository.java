package dev.weixin.stockroom;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySkuAndIdNot(String sku, Long id);

    // Serialize stock changes for the same product to avoid overselling.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdate(Long id);
}
