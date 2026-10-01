package dfgg.domain.itemstats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 포지션을 합친 챔피언·아이템 통계. {@link ChampionItemStats}가 표본 부족일 때
 * lift 분모가 한 단계 물러설 자리다(실측 중앙값 4회 → 11회).
 */
@Entity
@Table(
        name = "champion_item_rollup",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_champion_item_rollup",
                columnNames = {"champion_id", "item_id"}
        )
)
public class ChampionItemRollup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "champion_id", nullable = false)
    private Integer championId;

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

    @Column(name = "champion_game_count_all", nullable = false)
    private int championGameCountAll;

    @Column(name = "champion_game_count_recent", nullable = false)
    private int championGameCountRecent;

    protected ChampionItemRollup() {
    }

    public Long getId() {
        return id;
    }

    public Integer getChampionId() {
        return championId;
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
