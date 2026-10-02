package dfgg.domain.match;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantItemPurchaseRepository extends JpaRepository<ParticipantItemPurchase, Long> {

    List<ParticipantItemPurchase> findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc(String matchId);

    @Modifying
    @Query("DELETE FROM ParticipantItemPurchase purchase WHERE purchase.matchId = :matchId")
    void deletePurchasesByMatchId(@Param("matchId") String matchId);
}
