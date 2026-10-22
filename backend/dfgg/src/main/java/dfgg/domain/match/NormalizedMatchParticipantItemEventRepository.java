package dfgg.domain.match;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedMatchParticipantItemEventRepository
        extends JpaRepository<NormalizedMatchParticipantItemEvent, Long> {

    List<NormalizedMatchParticipantItemEvent> findByMatchIdOrderByParticipantIdAscEventOrderAsc(String matchId);

    @Query("""
            SELECT event FROM NormalizedMatchParticipantItemEvent event
            WHERE event.matchId = :matchId AND event.eventType = 'ITEM_PURCHASED'
            ORDER BY event.participantId, event.purchaseOrder
            """)
    List<NormalizedMatchParticipantItemEvent> findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc(
            @Param("matchId") String matchId);

    @Modifying
    @Query("DELETE FROM NormalizedMatchParticipantItemEvent event WHERE event.matchId = :matchId")
    void deleteEventsByMatchId(@Param("matchId") String matchId);
}
