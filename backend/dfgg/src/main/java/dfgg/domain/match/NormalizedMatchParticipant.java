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
            NormalizedMatch match,
            dfgg.domain.match.NormalizedParticipant participant
    ) {
        this.matchId = match.matchId();
        this.patch = match.patch();
        this.queueId = match.queueId();
        this.puuid = participant.puuid();
        this.participantId = participant.participantId();
        this.championId = participant.championId();
        this.teamId = participant.teamId();
        this.position = participant.position();
        this.tier = participant.tier();
        this.win = participant.win();
        this.finalCoreItemIds = participant.finalCoreItemIds();
        this.coreItemPurchaseOrder = participant.coreItemPurchaseOrder();
        this.coreItemPurchaseOrderComplete = participant.coreItemPurchaseOrderComplete();
    }

    public Long getId() {
        return id;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getPatch() {
        return patch;
    }

    public Integer getQueueId() {
        return queueId;
    }

    public String getPuuid() {
        return puuid;
    }

    public Integer getParticipantId() {
        return participantId;
    }

    public Integer getChampionId() {
        return championId;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public String getPosition() {
        return position;
    }

    public String getTier() {
        return tier;
    }

    public Boolean getWin() {
        return win;
    }

    public List<Integer> getFinalCoreItemIds() {
        return List.copyOf(finalCoreItemIds);
    }

    public List<Integer> getCoreItemPurchaseOrder() {
        return List.copyOf(coreItemPurchaseOrder);
    }

    public boolean isCoreItemPurchaseOrderComplete() {
        return coreItemPurchaseOrderComplete;
    }
}
