package dfgg.domain.match;

import dfgg.infrastructure.persistence.IntegerListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;

@Entity
@Table(
        name = "normalized_match_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_normalized_match_participant",
                columnNames = {"match_id", "puuid"}
        ),
        // 추천 안전 구역 조회(findNextItemDistribution)는 1~2코어 추천마다 호출되는 핫패스인데,
        // champion_id/position/patch로 필터링한다. 이 인덱스가 없으면 매 요청이 참가자
        // 테이블 전체를 순차 스캔한다.
        //
        // 컬럼 순서를 champion_id부터로 고정한 이유는 선택도가 아니다. 세 술어가 전부 동등
        // 비교라서 이 쿼리 하나만 보면 순서를 바꿔도 성능이 비슷하다. 진짜 이유는 좌측 prefix
        // 재사용이다 — findMostFrequentBuild가 (champion_id, position)만으로 조회하므로,
        // 이 순서여야 인덱스 하나가 쿼리 둘을 받친다. patch가 선두면 그쪽은 못 쓴다.
        // (덤으로 다중값 술어인 position이 선두가 아닌 것도 유리하다. 선두가 IN이면 값마다
        //  별도 인덱스 스캔이 된다.)
        //
        // tier는 일부러 뺐다. 추천이 요청자 티어로 거르지 않게 되면서 WHERE에서 사라졌는데,
        // 중간 컬럼으로 남겨두면 뒤따르는 patch가 인덱스 경계로 못 쓰이고 필터로만 동작한다.
        indexes = @Index(
                name = "idx_nmp_champion_position_patch",
                columnList = "champion_id, position, patch"
        )
)
public class NormalizedMatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, length = 32)
    private String matchId;

    @Column(name = "patch", nullable = false, length = 16)
    private String patch;

    @Column(name = "queue_id", nullable = false)
    private Integer queueId;

    @Column(name = "puuid", nullable = false, length = 128)
    private String puuid;

    @Column(name = "participant_id", nullable = false)
    private Integer participantId;

    @Column(name = "champion_id", nullable = false)
    private Integer championId;

    @Column(name = "team_id", nullable = false)
    private Integer teamId;

    @Column(name = "position", length = 32)
    private String position;

    @Column(name = "tier", length = 32)
    private String tier;

    @Column(name = "win", nullable = false)
    private Boolean win;

    @Convert(converter = IntegerListConverter.class)
    @Column(name = "final_core_item_ids", nullable = false, columnDefinition = "text")
    private List<Integer> finalCoreItemIds;

    @Convert(converter = IntegerListConverter.class)
    @Column(name = "core_item_purchase_order", nullable = false, columnDefinition = "text")
    private List<Integer> coreItemPurchaseOrder;

    @Column(name = "core_item_purchase_order_complete", nullable = false)
    private boolean coreItemPurchaseOrderComplete;

    protected NormalizedMatchParticipant() {
    }

    public NormalizedMatchParticipant(
            String puuid,
            Integer participantId,
            Integer championId,
            Integer teamId,
            String position,
            String tier,
            Boolean win,
            List<Integer> finalCoreItemIds,
            List<Integer> coreItemPurchaseOrder,
            boolean coreItemPurchaseOrderComplete
    ) {
        this.puuid = puuid;
        this.participantId = participantId;
        this.championId = championId;
        this.teamId = teamId;
        this.position = position;
        this.tier = tier;
        this.win = win;
        this.finalCoreItemIds = List.copyOf(finalCoreItemIds);
        this.coreItemPurchaseOrder = List.copyOf(coreItemPurchaseOrder);
        this.coreItemPurchaseOrderComplete = coreItemPurchaseOrderComplete;
    }

    /**
     * 매치 집계 객체에 참가자를 연결할 때 저장에 필요한 매치 공통 정보를 채운다.
     */
    void attachMatchContext(String matchId, String patch, Integer queueId) {
        this.matchId = matchId;
        this.patch = patch;
        this.queueId = queueId;
    }

    public Long getId() {
        return id;
    }

    public String getMatchId() {
        return matchId;
    }

    public String matchId() {
        return matchId;
    }

    public String getPatch() {
        return patch;
    }

    public String patch() {
        return patch;
    }

    public Integer getQueueId() {
        return queueId;
    }

    public Integer queueId() {
        return queueId;
    }

    public String getPuuid() {
        return puuid;
    }

    public String puuid() {
        return puuid;
    }

    public Integer getParticipantId() {
        return participantId;
    }

    public Integer participantId() {
        return participantId;
    }

    public Integer getChampionId() {
        return championId;
    }

    public Integer championId() {
        return championId;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public Integer teamId() {
        return teamId;
    }

    public String getPosition() {
        return position;
    }

    public String position() {
        return position;
    }

    public String getTier() {
        return tier;
    }

    public String tier() {
        return tier;
    }

    public Boolean getWin() {
        return win;
    }

    public Boolean win() {
        return win;
    }

    public List<Integer> getFinalCoreItemIds() {
        return List.copyOf(finalCoreItemIds);
    }

    public List<Integer> finalCoreItemIds() {
        return List.copyOf(finalCoreItemIds);
    }

    public List<Integer> getCoreItemPurchaseOrder() {
        return List.copyOf(coreItemPurchaseOrder);
    }

    public List<Integer> coreItemPurchaseOrder() {
        return List.copyOf(coreItemPurchaseOrder);
    }

    public boolean isCoreItemPurchaseOrderComplete() {
        return coreItemPurchaseOrderComplete;
    }

    public boolean coreItemPurchaseOrderComplete() {
        return coreItemPurchaseOrderComplete;
    }
}
