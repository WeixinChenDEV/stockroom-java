package dev.weixin.stockroom;

import org.springframework.data.jpa.repository.*;
import java.util.List;

interface MovementRepository extends JpaRepository<StockMovement, Long> {
    @EntityGraph(attributePaths = "product")
    List<StockMovement> findTop50ByOrderByRecordedAtDescIdDesc();
}
