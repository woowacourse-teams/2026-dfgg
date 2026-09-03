package dfgg.domain.itemstats;

import dfgg.domain.champion.ChampionPosition;
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
 * 챔피언·포지션별 아이템 구매 통계. Self-Synergy generator의 후보 점수이자,
 * Ally/Counter lift의 <b>분모</b>가 된다.
 *
 * <p>키에 patch가 없는 이유: 실측상 {@code champion+position+patch+item}은 중앙값 2회,
 * 지지도 20 이상이 12.4%뿐이라 확률의 분모로 쓸 수 없다. 대신 각 행이 전체 집계와
 * 최근 윈도 집계를 함께 들고 있어 패치 변화는 {@code *Recent} 쪽으로 드러난다.
 */
@Entity
@Table(
        name = "champion_item_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_champion_item_stats",
                columnNames = {"champion_id", "position", "item_id"}
        ),
        indexes = @Index(name = "idx_cis_champion_position", columnList = "champion_id, position")
)
public class ChampionItemStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "champion_id", nullable = false)
    private Integer championId;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 16)
    private ChampionPosition position;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "purchase_count_all", nullable = false)
    private int purchaseCountAll;

    @Column(name = "purchase_count_recent", nullable = false)
    private int purchaseCountRecent;

    @Column(name = "win_count_all", nullable = false)
    private int winCountAll;

    @Column(name = "win_count_recent", nullable = false)
    private int winCountRecent;

    /** P(item | champion, position)의 분모. 아이템과 무관하게 그 챔피언·포지션이 치른 판 수. */
    @Column(name = "champion_game_count_all", nullable = false)
    private int championGameCountAll;

    @Column(name = "champion_game_count_recent", nullable = false)
    private int championGameCountRecent;

    protected ChampionItemStats() {
    }

    public Long getId() {
        return id;
    }

    public Integer getChampionId() {
        return championId;
    }

    public ChampionPosition getPosition() {
        return position;
    }

    public Long getItemId() {
        return itemId;
    }

    public int getPurchaseCountAll() {
        return purchaseCountAll;
    }

    public int getPurchaseCountRecent() {
        return purchaseCountRecent;
    }

    public int getWinCountAll() {
        return winCountAll;
    }

    public int getWinCountRecent() {
        return winCountRecent;
    }

    public int getChampionGameCountAll() {
        return championGameCountAll;
    }

    public int getChampionGameCountRecent() {
        return championGameCountRecent;
    }
}
