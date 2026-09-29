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
@Table(name = "normalized_match_participant_item_purchases", uniqueConstraints = @UniqueConstraint(
        name = "uk_participant_item_purchase", columnNames = {"match_id", "participant_id", "purchase_order"}))
public class ParticipantItemPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, length = 32)
    private String matchId;

    @Column(name = "participant_id", nullable = false)
    private int participantId;

    @Column(name = "purchase_order", nullable = false)
    private int purchaseOrder;

    @Column(name = "item_id", nullable = false)
    private int itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false, length = 32)
    private ItemPurchaseType purchaseType;

    @Column(name = "game_time_ms")
    private Integer gameTimeMs;

    @Column(name = "current_gold")
    private Integer currentGold;

    @Column(name = "item_cost")
    private Integer itemCost;

    @Column(nullable = false, length = 16)
    private String patch;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ParticipantItemPurchase() {
    }

    public ParticipantItemPurchase(ParticipantItemEvent event, ItemPurchaseType purchaseType, String patch) {
        if (!"ITEM_PURCHASED".equals(event.eventType()) || event.purchaseOrder() == null
                || event.itemId() == null || event.itemId() <= 0) {
            throw new IllegalArgumentException("구매 레코드는 유효한 구매 순번과 아이템 ID가 있는 구매 이벤트로 생성해야 합니다.");
        }
        if (purchaseType == null) {
            throw new IllegalArgumentException("구매 아이템의 서비스 분류가 필요합니다.");
        }
        if (patch == null || patch.isBlank() || patch.length() > 16) {
            throw new IllegalArgumentException("패치는 비어 있지 않은 16자 이하의 문자열이어야 합니다.");
        }
        if (event.gameTimeMs() != null && event.gameTimeMs() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("구매 이벤트 시각이 저장 가능한 정수 범위를 초과했습니다.");
        }
        this.matchId = event.matchId();
        this.participantId = event.participantId();
        this.purchaseOrder = event.purchaseOrder();
        this.itemId = event.itemId();
        this.purchaseType = purchaseType;
        this.gameTimeMs = event.gameTimeMs() == null ? null : Math.toIntExact(event.gameTimeMs());
        this.patch = patch;
        this.createdAt = LocalDateTime.now();
        // 원본 구매 이벤트에는 골드/추가 지불액이 없다. 검증 전에는 NULL을 유지한다.
    }

    /** 패치 카탈로그와 실제 제거 재료로 계산한 양수 비용 가설을 기록한다. */
    public void recordCostHypothesis(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("구매 비용 가설은 양수여야 합니다.");
        }
        this.itemCost = amount;
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

    public int getPurchaseOrder() {
        return purchaseOrder;
    }

    public int getItemId() {
        return itemId;
    }

    public ItemPurchaseType getPurchaseType() {
        return purchaseType;
    }

    public Integer getGameTimeMs() {
        return gameTimeMs;
    }

    public Integer getCurrentGold() {
        return currentGold;
    }

    public Integer getItemCost() {
        return itemCost;
    }

    public String getPatch() {
        return patch;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
