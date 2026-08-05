package dfgg.domain.stats;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;

import java.util.List;

public class ChampionBuildStats {
    private Long id;
    private Champion champion;
    private ChampionPosition championPosition;

    private Boolean enemyTankHeavy;
    private Boolean enemyApHeavy;
    private Boolean enemyAssassinHeavy;

    private Boolean allyHasMarksman;
    private Boolean allyTankHeavy;

    private String tier;
    private List<Item> items;

    private Integer winCount;
    private Integer gameCount;

    public ChampionBuildStats(Long id, Champion champion, ChampionPosition championPosition, Boolean enemyTankHeavy, Boolean enemyApHeavy, Boolean enemyAssassinHeavy, Boolean allyHasMarksman, Boolean allyTankHeavy, String tier, List<Item> items, Integer winCount, Integer gameCount) {
        this.id = id;
        this.champion = champion;
        this.championPosition = championPosition;
        this.enemyTankHeavy = enemyTankHeavy;
        this.enemyApHeavy = enemyApHeavy;
        this.enemyAssassinHeavy = enemyAssassinHeavy;
        this.allyHasMarksman = allyHasMarksman;
        this.allyTankHeavy = allyTankHeavy;
        this.tier = tier;
        this.items = items;
        this.winCount = winCount;
        this.gameCount = gameCount;
    }

    public List<Item> getItems() {
        return items;
    }
}
