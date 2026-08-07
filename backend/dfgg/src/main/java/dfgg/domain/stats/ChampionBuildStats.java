package dfgg.domain.stats;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "composition_stats")
public class ChampionBuildStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patch", nullable = false, length = 16)
    private String patch;

    @Column(name = "queue_id", nullable = false)
    private Integer queueId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "champion_id", nullable = false)
    private Champion champion;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false)
    private ChampionPosition championPosition;

    @Column(name = "enemy_tank_heavy")
    private Boolean enemyTankHeavy;

    @Column(name = "enemy_ap_heavy")
    private Boolean enemyApHeavy;

    @Column(name = "enemy_assassin_heavy")
    private Boolean enemyAssassinHeavy;

    @Column(name = "ally_has_marksman")
    private Boolean allyHasMarksman;

    @Column(name = "ally_tank_heavy")
    private Boolean allyTankHeavy;

    @Column(name = "tier", length = 32)
    private String tier;

    @Column(name = "build_key", nullable = false, length = 512)
    private String buildKey;

    @Column(name = "stats_key", nullable = false, unique = true, length = 1024)
    private String statsKey;

    @ManyToMany
    @JoinTable(
            name = "composition_stats_items",
            joinColumns = @JoinColumn(name = "composition_stats_id"),
            inverseJoinColumns = @JoinColumn(name = "item_id")
    )
    @OrderColumn(name = "item_order")
    private List<Item> items = new ArrayList<>();

    @Column(name = "win_count")
    private Integer winCount;

    @Column(name = "game_count")
    private Integer gameCount;

    protected ChampionBuildStats() {
    }

    public ChampionBuildStats(
            String patch,
            Integer queueId,
            Champion champion,
            ChampionPosition championPosition,
            Boolean enemyTankHeavy,
            Boolean enemyApHeavy,
            Boolean enemyAssassinHeavy,
            Boolean allyHasMarksman,
            Boolean allyTankHeavy,
            String tier,
            String buildKey,
            List<Item> items,
            Integer winCount,
            Integer gameCount
    ) {
        if (patch == null || patch.isBlank()) {
            throw new IllegalArgumentException("patch must not be blank");
        }
        if (queueId == null) {
            throw new IllegalArgumentException("queueId must not be null");
        }
        if (buildKey == null || buildKey.isBlank()) {
            throw new IllegalArgumentException("buildKey must not be blank");
        }

        this.patch = patch;
        this.queueId = queueId;
        this.champion = champion;
        this.championPosition = championPosition;
        this.enemyTankHeavy = enemyTankHeavy;
        this.enemyApHeavy = enemyApHeavy;
        this.enemyAssassinHeavy = enemyAssassinHeavy;
        this.allyHasMarksman = allyHasMarksman;
        this.allyTankHeavy = allyTankHeavy;
        this.tier = tier;
        this.buildKey = buildKey;
        this.statsKey = createStatsKey(
                patch,
                queueId,
                champion,
                championPosition,
                enemyTankHeavy,
                enemyApHeavy,
                enemyAssassinHeavy,
                allyHasMarksman,
                allyTankHeavy,
                tier,
                buildKey
        );
        this.items = new ArrayList<>(items);
        this.winCount = winCount;
        this.gameCount = gameCount;
    }

    public static String createStatsKey(
            String patch,
            Integer queueId,
            Champion champion,
            ChampionPosition championPosition,
            Boolean enemyTankHeavy,
            Boolean enemyApHeavy,
            Boolean enemyAssassinHeavy,
            Boolean allyHasMarksman,
            Boolean allyTankHeavy,
            String tier,
            String buildKey
    ) {
        return String.join(
                "|",
                String.valueOf(patch),
                String.valueOf(queueId),
                String.valueOf(champion.getChampionId()),
                String.valueOf(championPosition),
                String.valueOf(enemyTankHeavy),
                String.valueOf(enemyApHeavy),
                String.valueOf(enemyAssassinHeavy),
                String.valueOf(allyHasMarksman),
                String.valueOf(allyTankHeavy),
                String.valueOf(tier),
                String.valueOf(buildKey)
        );
    }

    public void recordGame(boolean win) {
        this.gameCount = (this.gameCount == null ? 0 : this.gameCount) + 1;
        if (win) {
            this.winCount = (this.winCount == null ? 0 : this.winCount) + 1;
        }
    }

    public Long getId() {
        return id;
    }

    public String getPatch() {
        return patch;
    }

    public Integer getQueueId() {
        return queueId;
    }

    public Champion getChampion() {
        return champion;
    }

    public ChampionPosition getChampionPosition() {
        return championPosition;
    }

    public Boolean getEnemyTankHeavy() {
        return enemyTankHeavy;
    }

    public Boolean getEnemyApHeavy() {
        return enemyApHeavy;
    }

    public Boolean getEnemyAssassinHeavy() {
        return enemyAssassinHeavy;
    }

    public Boolean getAllyHasMarksman() {
        return allyHasMarksman;
    }

    public Boolean getAllyTankHeavy() {
        return allyTankHeavy;
    }

    public String getTier() {
        return tier;
    }

    public String getBuildKey() {
        return buildKey;
    }

    public String getStatsKey() {
        return statsKey;
    }

    public Integer getWinCount() {
        return winCount;
    }

    public Integer getGameCount() {
        return gameCount;
    }

    public List<Item> getItems() {
        return List.copyOf(items);
    }
}
