package dfgg.domain.stats;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
    private Long id;

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

    private String tier;

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
            Long id,
            Champion champion,
            ChampionPosition championPosition,
            Boolean enemyTankHeavy,
            Boolean enemyApHeavy,
            Boolean enemyAssassinHeavy,
            Boolean allyHasMarksman,
            Boolean allyTankHeavy,
            String tier,
            List<Item> items,
            Integer winCount,
            Integer gameCount
    ) {
        this.id = id;
        this.champion = champion;
        this.championPosition = championPosition;
        this.enemyTankHeavy = enemyTankHeavy;
        this.enemyApHeavy = enemyApHeavy;
        this.enemyAssassinHeavy = enemyAssassinHeavy;
        this.allyHasMarksman = allyHasMarksman;
        this.allyTankHeavy = allyTankHeavy;
        this.tier = tier;
        this.items = new ArrayList<>(items);
        this.winCount = winCount;
        this.gameCount = gameCount;
    }

    public List<Item> getItems() {
        return List.copyOf(items);
    }
}
