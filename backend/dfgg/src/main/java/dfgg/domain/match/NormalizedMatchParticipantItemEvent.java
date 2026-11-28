package dfgg.domain.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "normalized_match_participant_item_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_participant_item_purchase",
                columnNames = {"match_id", "participant_id", "purchase_order"}),
        @UniqueConstraint(name = "uk_participant_item_event_order",
                columnNames = {"match_id", "participant_id", "event_order"}),
        @UniqueConstraint(name = "uk_participant_item_event_source",
                columnNames = {"match_id", "participant_id", "frame_index", "event_index"})
})
public class NormalizedMatchParticipantItemEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, length = 32)
    private String matchId;

    @Column(name = "participant_id", nullable = false)
    private int participantId;

    @Column(name = "position", length = 32)
    private String position;

    @Column(name = "purchase_order")
    private Integer purchaseOrder;

    @Column(name = "event_order")
    private Integer eventOrder;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "frame_index")
    private Integer frameIndex;

    @Column(name = "event_index")
    private Integer eventIndex;

    @Column(name = "undo_before_item_id")
    private Integer undoBeforeItemId;

    @Column(name = "undo_after_item_id")
    private Integer undoAfterItemId;

    @Column(name = "normalization_version", length = 32)
    private String normalizationVersion;

    @Column(name = "item_id")
    private Integer itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", length = 32)
    private ItemPurchaseType purchaseType;

    @Column(name = "game_time_ms")
    private Integer gameTimeMs;

    @Column(name = "item_cost")
    private Integer itemCost;

    @Column(name = "gold_lower")
    private Integer goldLower;

    @Column(name = "gold_upper")
    private Integer goldUpper;

    @Enumerated(EnumType.STRING)
    @Column(name = "gold_range_status", length = 32)
    private GoldRangeStatus goldRangeStatus;

    @Column(nullable = false, length = 16)
    private String patch;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected NormalizedMatchParticipantItemEvent() {
    }

    public NormalizedMatchParticipantItemEvent(ParticipantItemEvent event, ItemPurchaseType purchaseType,
                                   String patch, String position) {
        this(event, patch, position);
        if (!isPurchase() || event.purchaseOrder() == null || event.purchaseOrder() <= 0 || event.itemId() == null || event.itemId() <= 0
                || purchaseType == null) {
            throw new IllegalArgumentException("구매 이벤트에는 유효한 구매 순번, 아이템 ID와 분류가 필요합니다.");
        }
        this.purchaseType = purchaseType;
        // 구매 순간의 정확한 골드는 관측되지 않는다. 비용과 Gold Range는 정규화 단계에서 계산한다.
    }

    /** 비구매 이벤트도 구매와 같은 테이블에 보존한다. 구매의 파생 값은 구매 생성자에서 채운다. */
    private NormalizedMatchParticipantItemEvent(ParticipantItemEvent event, String patch, String position) {
        if (event == null || event.matchId() == null || event.matchId().isBlank()
                || event.matchId().length() > 32 || event.participantId() <= 0 || event.eventOrder() <= 0
                || event.eventType() == null || !event.eventType().startsWith("ITEM_")
                || event.eventType().length() > 32 || event.sourceFrameIndex() < 0 || event.sourceEventIndex() < 0
                || event.normalizationVersion() == null || event.normalizationVersion().isBlank()
                || event.normalizationVersion().length() > 32) {
            throw new IllegalArgumentException("유효한 참가자 아이템 이벤트가 필요합니다.");
        }
        if (patch == null || patch.isBlank() || patch.length() > 16
                || position != null && position.length() > 32) {
            throw new IllegalArgumentException("유효한 패치와 포지션이 필요합니다.");
        }
        if (event.gameTimeMs() != null && (event.gameTimeMs() < 0 || event.gameTimeMs() > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("아이템 이벤트 시각이 저장 가능한 정수 범위를 벗어났습니다.");
        }
        this.matchId = event.matchId();
        this.participantId = event.participantId();
        this.position = position;
        this.purchaseOrder = event.purchaseOrder();
        this.eventOrder = event.eventOrder();
        this.eventType = event.eventType();
        this.itemId = event.itemId();
        this.frameIndex = event.sourceFrameIndex();
        this.eventIndex = event.sourceEventIndex();
        this.undoBeforeItemId = event.beforeItemId();
        this.undoAfterItemId = event.afterItemId();
        this.normalizationVersion = event.normalizationVersion();
        this.gameTimeMs = event.gameTimeMs() == null ? null : Math.toIntExact(event.gameTimeMs());
        this.patch = patch;
        this.createdAt = LocalDateTime.now();
    }

    public static NormalizedMatchParticipantItemEvent nonPurchase(
            ParticipantItemEvent event, String patch, String position) {
        if (event == null || "ITEM_PURCHASED".equals(event.eventType()) || event.purchaseOrder() != null) {
            throw new IllegalArgumentException("구매 외의 아이템 이벤트가 필요합니다.");
        }
        return new NormalizedMatchParticipantItemEvent(event, patch, position);
    }

    public boolean isPurchase() {
        return "ITEM_PURCHASED".equals(eventType);
    }

    /** 기존 구매 행은 재정규화 전까지 원천 좌표가 없어 Replay에 사용할 수 없다. */
    public boolean hasReplaySource() {
        return eventOrder != null && frameIndex != null && eventIndex != null && normalizationVersion != null;
    }

    public ParticipantItemEvent toParticipantItemEvent() {
        if (!hasReplaySource()) {
            throw new IllegalStateException("전체 아이템 이벤트 재정규화가 필요합니다.");
        }
        return new ParticipantItemEvent(matchId, participantId, eventOrder, purchaseOrder,
                frameIndex, eventIndex, eventType, itemId, undoBeforeItemId, undoAfterItemId,
                gameTimeMs == null ? null : gameTimeMs.longValue(), null, normalizationVersion);
    }

    public Integer getEventOrder() {
        return eventOrder;
    }

    /** 패치 카탈로그와 실제 제거 재료로 계산한 양수 비용 가설을 기록한다. */
    public void recordCostHypothesis(int amount) {
        if (!isPurchase() || amount <= 0) {
            throw new IllegalArgumentException("구매 비용 가설은 양수여야 합니다.");
        }
        this.itemCost = amount;
    }

    /** 구매 직전 골드 범위를 기록한다. 계산할 수 없으면 이 메서드를 호출하지 않아 NULL을 유지한다. */
    public void recordGoldRange(GoldRange range, GoldRangeStatus status) {
        if (!isPurchase() || range == null || status == null) {
            throw new IllegalArgumentException("구매 골드 범위와 계산 근거가 필요합니다.");
        }
        if (range.upper() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("구매 골드 범위가 저장 가능한 정수 범위를 초과했습니다.");
        }
        this.goldLower = Math.toIntExact(range.lower());
        this.goldUpper = Math.toIntExact(range.upper());
        this.goldRangeStatus = status;
    }

    public Long getId() {
        return id;
    }

    public String getMatchId() {
        return matchId;
    }

    public int getParticipantId() {
        return participantId;
    }

    public String getPosition() {
        return position;
    }

    public Integer getPurchaseOrder() {
        return purchaseOrder;
    }

    public String getEventType() {
        return eventType;
    }

    public Integer getFrameIndex() {
        return frameIndex;
    }

    public Integer getEventIndex() {
        return eventIndex;
    }

    public Integer getUndoBeforeItemId() {
        return undoBeforeItemId;
    }

    public Integer getUndoAfterItemId() {
        return undoAfterItemId;
    }

    public String getNormalizationVersion() {
        return normalizationVersion;
    }

    public Integer getItemId() {
        return itemId;
    }

    public ItemPurchaseType getPurchaseType() {
        return purchaseType;
    }

    public Integer getGameTimeMs() {
        return gameTimeMs;
    }

    public Integer getItemCost() {
        return itemCost;
    }

    public Integer getGoldLower() {
        return goldLower;
    }

    public Integer getGoldUpper() {
        return goldUpper;
    }

    public GoldRangeStatus getGoldRangeStatus() {
        return goldRangeStatus;
    }

    public String getPatch() {
        return patch;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
