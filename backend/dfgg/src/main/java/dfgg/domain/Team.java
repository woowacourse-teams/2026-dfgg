package dfgg.domain;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;

import java.util.List;

public class Team {
    private static final int TANK_THRESHOLD = 2;
    private static final int AP_THRESHOLD = 2;
    private static final int ASSASSIN_THRESHOLD = 2;
    private static final int MARKSMAN_THRESHOLD = 2;

    private final List<Champion> champions;

    public Team(List<Champion> champions) {
        this.champions = champions;
    }

    public boolean isTankHeavy() {
        return countByAnyTag(ChampionTag.TANK) >= TANK_THRESHOLD;
    }

    public boolean isApHeavy() {
        return countByAnyTag(ChampionTag.MAGE) >= AP_THRESHOLD;
    }

    public boolean isAssassinHeavy() {
        return countByAnyTag(ChampionTag.ASSASSIN) >= ASSASSIN_THRESHOLD;
    }

    public boolean hasMarksman() {
        return countByAnyTag(ChampionTag.MARKSMAN) >= MARKSMAN_THRESHOLD;
    }

    private int countByAnyTag(ChampionTag tag) {
        return (int) champions.stream()
                .flatMap(champion -> champion.getChampionTags().stream())
                .filter(tag::equals)
                .count();
    }
}
