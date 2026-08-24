package dfgg.domain.match;

import dfgg.infrastructure.persistence.IntegerListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;

@Entity
@Table(
        name = "normalized_match_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_normalized_match_participant",
                columnNames = {"match_id", "puuid"}
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
