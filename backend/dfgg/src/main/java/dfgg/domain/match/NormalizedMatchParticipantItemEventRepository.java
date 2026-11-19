package dfgg.domain.match;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
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

    /**
     * 아이템 이벤트를 한 문장으로 저장한다. 경기당 약 450행이라 행마다 보내면 왕복이 정규화 시간의 대부분을 차지한다.
     * JPA 저장은 IDENTITY 키 때문에 행마다 DB를 왕복하므로, 칸별 배열로 넘겨 unnest로 행을 펼친다.
     */
    default void insertAll(List<NormalizedMatchParticipantItemEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        insertColumns(
                column(events, NormalizedMatchParticipantItemEvent::getMatchId, String[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getParticipantId, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getPosition, String[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getPurchaseOrder, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getEventOrder, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getEventType, String[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getFrameIndex, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getEventIndex, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getUndoBeforeItemId, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getUndoAfterItemId, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getNormalizationVersion, String[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getItemId, Integer[]::new),
                column(events, event -> event.getPurchaseType() == null ? null : event.getPurchaseType().name(),
                        String[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getGameTimeMs, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getItemCost, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getGoldLower, Integer[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getGoldUpper, Integer[]::new),
                column(events, event -> event.getGoldRangeStatus() == null ? null : event.getGoldRangeStatus().name(),
                        String[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getPatch, String[]::new),
                column(events, NormalizedMatchParticipantItemEvent::getCreatedAt, LocalDateTime[]::new)
        );
    }

    @Modifying
    @Query(value = """
            INSERT INTO normalized_match_participant_item_events (
                match_id, participant_id, position, purchase_order, event_order, event_type,
                frame_index, event_index, undo_before_item_id, undo_after_item_id, normalization_version,
                item_id, purchase_type, game_time_ms, item_cost, gold_lower, gold_upper, gold_range_status,
                patch, created_at
            )
            SELECT *
            FROM unnest(
                CAST(:matchIds AS varchar[]), CAST(:participantIds AS integer[]), CAST(:positions AS varchar[]),
                CAST(:purchaseOrders AS integer[]), CAST(:eventOrders AS integer[]),
                CAST(:eventTypes AS varchar[]),
                CAST(:frameIndexes AS integer[]), CAST(:eventIndexes AS integer[]),
                CAST(:undoBeforeItemIds AS integer[]), CAST(:undoAfterItemIds AS integer[]),
                CAST(:normalizationVersions AS varchar[]),
                CAST(:itemIds AS integer[]), CAST(:purchaseTypes AS varchar[]), CAST(:gameTimeMs AS integer[]),
                CAST(:itemCosts AS integer[]), CAST(:goldLowers AS integer[]), CAST(:goldUppers AS integer[]),
                CAST(:goldRangeStatuses AS varchar[]),
                CAST(:patches AS varchar[]), CAST(:createdAts AS timestamp[])
            )
            """, nativeQuery = true)
    void insertColumns(
            @Param("matchIds") String[] matchIds,
            @Param("participantIds") Integer[] participantIds,
            @Param("positions") String[] positions,
            @Param("purchaseOrders") Integer[] purchaseOrders,
            @Param("eventOrders") Integer[] eventOrders,
            @Param("eventTypes") String[] eventTypes,
            @Param("frameIndexes") Integer[] frameIndexes,
            @Param("eventIndexes") Integer[] eventIndexes,
            @Param("undoBeforeItemIds") Integer[] undoBeforeItemIds,
            @Param("undoAfterItemIds") Integer[] undoAfterItemIds,
            @Param("normalizationVersions") String[] normalizationVersions,
            @Param("itemIds") Integer[] itemIds,
            @Param("purchaseTypes") String[] purchaseTypes,
            @Param("gameTimeMs") Integer[] gameTimeMs,
            @Param("itemCosts") Integer[] itemCosts,
            @Param("goldLowers") Integer[] goldLowers,
            @Param("goldUppers") Integer[] goldUppers,
            @Param("goldRangeStatuses") String[] goldRangeStatuses,
            @Param("patches") String[] patches,
            @Param("createdAts") LocalDateTime[] createdAts
    );

    private static <T, V> V[] column(List<T> rows, Function<T, V> value, IntFunction<V[]> array) {
        return rows.stream().map(value).toArray(array);
    }
}
