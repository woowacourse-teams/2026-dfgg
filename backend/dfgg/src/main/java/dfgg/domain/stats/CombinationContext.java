package dfgg.domain.stats;

import dfgg.domain.team.Team;

public record CombinationContext(
        boolean enemyTankHeavy,
        boolean enemyApHeavy,
        boolean enemyAssassinHeavy,
        boolean allyHasMarksman,
        boolean allyTankHeavy
) {

    public static CombinationContext analyze(Team enemies, Team allies) {
        return new CombinationContext(
                enemies.isTankHeavy(),
                enemies.isApHeavy(),
                enemies.isAssassinHeavy(),
                allies.hasMarksman(),
                allies.isTankHeavy()
        );
    }
}
