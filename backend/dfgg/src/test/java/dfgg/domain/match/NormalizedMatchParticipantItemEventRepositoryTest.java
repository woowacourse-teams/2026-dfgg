package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * insertAll은 JPA 저장(saveAllAndFlush)을 칸별 배열 INSERT 한 문장으로 바꾼 것이다.
 * 같은 이벤트를 두 방식으로 저장해, 행의 id를 뺀 모든 칸이 같아야 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
class NormalizedMatchParticipantItemEventRepositoryTest {

    private static final String MATCH_ID = "KR_BULK";
    private static final String PATCH = "16.19";
    private static final String VERSION = "item-events-v1";
    private static final Set<String> STORED_COLUMNS = Set.of(
            "match_id", "participant_id", "position", "purchase_order", "event_order", "event_type",
            "frame_index", "event_index", "undo_before_item_id", "undo_after_item_id", "normalization_version",
            "item_id", "purchase_type", "game_time_ms", "item_cost", "gold_lower", "gold_upper",
            "gold_range_status", "patch", "created_at");

    @Autowired
    private NormalizedMatchParticipantItemEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("id를 뺀 모든 칸을 saveAllAndFlush와 같게 저장한다")
    void insertAll_StoresEveryColumnLikeSaveAllAndFlush() {
        List<NormalizedMatchParticipantItemEvent> events = events();

        List<Map<String, Object>> saved = storedByJpa(events);
        repository.insertAll(events);

        List<Map<String, Object>> inserted = rows();
        assertThat(inserted).hasSize(events.size());
        assertThat(inserted).isEqualTo(saved);
    }

    @Test
    @DisplayName("테스트 비교가 테이블의 모든 칸을 포함한다")
    void rows_CoverEveryTableColumnExceptId() {
        repository.insertAll(events());

        assertThat(jdbc.queryForList("SELECT * FROM normalized_match_participant_item_events").getFirst().keySet())
                .containsExactlyInAnyOrderElementsOf(withId(STORED_COLUMNS));
    }

    @Test
    @DisplayName("구매·판매·되돌리기·파괴와 NULL 칸을 모두 그대로 저장한다")
    void insertAll_KeepsNullColumnsAndEveryEventType() {
        repository.insertAll(events());

        List<Map<String, Object>> rows = rows();
        assertThat(rows).extracting(row -> row.get("event_type")).containsExactlyInAnyOrder(
                "ITEM_PURCHASED", "ITEM_PURCHASED", "ITEM_PURCHASED", "ITEM_SOLD", "ITEM_UNDO", "ITEM_DESTROYED",
                "ITEM_PURCHASED");
        Map<String, Object> undo = rowOf("ITEM_UNDO");
        assertThat(undo.get("undo_before_item_id")).isEqualTo(3071);
        assertThat(undo.get("undo_after_item_id")).isEqualTo(0);
        assertThat(undo).containsEntry("item_id", null).containsEntry("purchase_order", null)
                .containsEntry("purchase_type", null).containsEntry("gold_range_status", null);
        Map<String, Object> sold = rowOf("ITEM_SOLD");
        assertThat(sold).containsEntry("undo_before_item_id", null).containsEntry("undo_after_item_id", null)
                .containsEntry("position", null);
        assertThat(rows.stream().filter(row -> row.get("gold_range_status") != null))
                .extracting(row -> row.get("gold_range_status"))
                .containsExactlyInAnyOrder("BASE", "TOTAL_GOLD_REFINED");
        assertThat(rows.stream().filter(row -> row.get("purchase_type") != null))
                .extracting(row -> row.get("purchase_type"))
                .containsExactlyInAnyOrder("COMPONENT", "CORE", "BOOTS", "CONSUMABLE");
    }

    @Test
    @DisplayName("created_at이 마이크로초보다 정밀해도 saveAllAndFlush와 같게 저장한다")
    void insertAll_StoresSubMicrosecondCreatedAtLikeSaveAllAndFlush() {
        List<NormalizedMatchParticipantItemEvent> events = new ArrayList<>(List.of(
                purchaseCreatedAt(1, LocalDateTime.of(2026, 10, 27, 11, 33, 35, 123_456_789)),
                purchaseCreatedAt(2, LocalDateTime.of(2026, 10, 27, 11, 33, 35, 123_456_499)),
                purchaseCreatedAt(3, LocalDateTime.of(2026, 10, 27, 11, 33, 35, 999_999_999)),
                purchaseCreatedAt(4, LocalDateTime.of(2026, 10, 27, 11, 33, 35, 0))));

        List<Map<String, Object>> saved = storedByJpa(events);
        repository.insertAll(events);

        assertThat(rows()).isEqualTo(saved);
    }

    @Test
    @DisplayName("빈 목록이면 아무것도 저장하지 않는다")
    void insertAll_WhenEmpty_StoresNothing() {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM normalized_match_participant_item_events",
                Integer.class);

        repository.insertAll(List.of());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM normalized_match_participant_item_events",
                Integer.class)).isEqualTo(before);
    }

    /** JPA 방식으로 저장해 읽은 뒤 그 행을 지워, 같은 이벤트를 insertAll로 다시 넣을 자리를 만든다. */
    private List<Map<String, Object>> storedByJpa(List<NormalizedMatchParticipantItemEvent> events) {
        repository.saveAllAndFlush(events);
        List<Map<String, Object>> saved = rows();
        repository.deleteEventsByMatchId(MATCH_ID);
        entityManager.clear();
        assertThat(rows()).isEmpty();
        return saved;
    }

    private List<Map<String, Object>> rows() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM normalized_match_participant_item_events
                WHERE match_id = ? ORDER BY participant_id, event_order, id
                """, MATCH_ID);
        rows.forEach(row -> row.remove("id"));
        return rows;
    }

    private Map<String, Object> rowOf(String eventType) {
        return rows().stream().filter(row -> eventType.equals(row.get("event_type"))).findFirst().orElseThrow();
    }

    private Set<String> withId(Set<String> columns) {
        Set<String> all = new java.util.HashSet<>(columns);
        all.add("id");
        return all;
    }

    private List<NormalizedMatchParticipantItemEvent> events() {
        NormalizedMatchParticipantItemEvent component = purchase(1, 1, 1, 0, 1036, ItemPurchaseType.COMPONENT, "TOP");
        component.recordCostHypothesis(350);
        component.recordGoldRange(new GoldRange(500, 1_250), GoldRangeStatus.BASE);
        NormalizedMatchParticipantItemEvent core = purchase(1, 2, 2, 1, 3071, ItemPurchaseType.CORE, "TOP");
        core.recordCostHypothesis(850);
        core.recordGoldRange(new GoldRange(500, 1_000), GoldRangeStatus.TOTAL_GOLD_REFINED);
        // 비용과 골드 범위를 계산하지 못한 구매는 해당 칸이 NULL이다.
        NormalizedMatchParticipantItemEvent boots = purchase(1, 3, 3, 2, 3006, ItemPurchaseType.BOOTS, "TOP");
        // 포지션이 없고 시각도 모르는 구매.
        NormalizedMatchParticipantItemEvent consumable = purchaseOf(
                new ParticipantItemEvent(MATCH_ID, 2, 1, 1, 0, 0, "ITEM_PURCHASED", 2003, null, null, null, null,
                        VERSION), ItemPurchaseType.CONSUMABLE, null);
        NormalizedMatchParticipantItemEvent sold = nonPurchase(
                new ParticipantItemEvent(MATCH_ID, 1, 4, null, 1, 1, "ITEM_SOLD", 1036, null, null, 61_000L, null,
                        VERSION), null);
        NormalizedMatchParticipantItemEvent undo = nonPurchase(
                new ParticipantItemEvent(MATCH_ID, 1, 5, null, 1, 2, "ITEM_UNDO", null, 3071, 0, 62_000L, null,
                        VERSION), "TOP");
        NormalizedMatchParticipantItemEvent destroyed = nonPurchase(
                new ParticipantItemEvent(MATCH_ID, 1, 6, null, 1, 3, "ITEM_DESTROYED", 1036, null, null, null, null,
                        VERSION), "TOP");
        return new ArrayList<>(List.of(component, core, boots, consumable, sold, undo, destroyed));
    }

    private NormalizedMatchParticipantItemEvent purchase(int participantId, int eventOrder, int purchaseOrder,
                                                         int eventIndex, int itemId, ItemPurchaseType type,
                                                         String position) {
        return purchaseOf(new ParticipantItemEvent(MATCH_ID, participantId, eventOrder, purchaseOrder, 0, eventIndex,
                "ITEM_PURCHASED", itemId, null, null, 1_000L * eventOrder, null, VERSION), type, position);
    }

    private NormalizedMatchParticipantItemEvent purchaseCreatedAt(int order, LocalDateTime createdAt) {
        NormalizedMatchParticipantItemEvent event = purchase(1, order, order, order, 1036,
                ItemPurchaseType.COMPONENT, "TOP");
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        return event;
    }

    private NormalizedMatchParticipantItemEvent purchaseOf(ParticipantItemEvent event, ItemPurchaseType type,
                                                           String position) {
        return new NormalizedMatchParticipantItemEvent(event, type, PATCH, position);
    }

    private NormalizedMatchParticipantItemEvent nonPurchase(ParticipantItemEvent event, String position) {
        return NormalizedMatchParticipantItemEvent.nonPurchase(event, PATCH, position);
    }
}
