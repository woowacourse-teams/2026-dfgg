package dfgg.domain.itemstats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * {@code [내 챔피언 + 상대 챔피언 + 아이템]} 삼중항 통계. Ally-Synergy와 Counter generator가 읽는다.
 *
 * <p>이 테이블이 기존 구조의 결함을 고치는 자리다. 예전 카운터 학습은
 * {@code [적 챔피언 + 우리 팀 아무개의 아이템]}이라 <b>그 아이템을 실제로 산 사람이 누구인지가
 * 사라졌다</b>. 여기서는 {@code myChampionId}가 구매자 본인이며, 맥락이 되는 상대는
 * {@code otherChampionId}로 따로 남는다.
 *
 * <p>키에 patch가 없다. 661,219개 삼중항을 49개 패치로 쪼개면 표본이 무너진다 —
 * 패치 변화는 {@code *Recent} 카운트로 표현한다.
 */
@Entity
@Table(
        name = "champion_pair_item_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_champion_pair_item_stats",
                columnNames = {"my_champion_id", "other_champion_id", "relation", "item_id"}
        ),
        indexes = @Index(
                name = "idx_cpis_lookup",
                columnList = "my_champion_id, relation, other_champion_id"
        )
)
public class ChampionPairItemStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 아이템을 실제로 산 챔피언. */
    @Column(name = "my_champion_id", nullable = false)
    private Integer myChampionId;

    /** 맥락이 되는 챔피언(아군 또는 적). 이쪽 아이템은 세지 않는다. */
    @Column(name = "other_champion_id", nullable = false)
    private Integer otherChampionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 8)
    private PairRelation relation;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "co_count_all", nullable = false)
    private int coCountAll;

    @Column(name = "co_count_recent", nullable = false)
    private int coCountRecent;

    @Column(name = "win_count_all", nullable = false)
    private int winCountAll;

    @Column(name = "win_count_recent", nullable = false)
    private int winCountRecent;

    /** P(item | 내 챔피언, 상대 챔피언)의 분모. 아이템과 무관하게 그 조합이 함께 나온 판 수. */
    @Column(name = "pair_game_count_all", nullable = false)
    private int pairGameCountAll;

    @Column(name = "pair_game_count_recent", nullable = false)
    private int pairGameCountRecent;

    protected ChampionPairItemStats() {
    }

    public Long getId() {
        return id;
    }

    public Integer getMyChampionId() {
        return myChampionId;
    }

    public Integer getOtherChampionId() {
        return otherChampionId;
    }

    public PairRelation getRelation() {
        return relation;
    }

    public Long getItemId() {
        return itemId;
    }

    public int getCoCountAll() {
        return coCountAll;
    }

    public int getCoCountRecent() {
        return coCountRecent;
    }

    public int getWinCountAll() {
        return winCountAll;
    }

    public int getWinCountRecent() {
        return winCountRecent;
    }

    public int getPairGameCountAll() {
        return pairGameCountAll;
    }

    public int getPairGameCountRecent() {
        return pairGameCountRecent;
    }
}
