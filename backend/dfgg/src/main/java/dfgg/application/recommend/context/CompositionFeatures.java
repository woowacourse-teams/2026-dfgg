package dfgg.application.recommend.context;

import java.util.List;

/** Core와 Component가 공유하는 챔피언 태그 개수. 피해량이나 실제 내구도가 아니다. */
public record CompositionFeatures(int enemyTankCount, int enemyMageCount, int enemyMarksmanCount,
        int enemyAssassinCount, int enemyFighterCount, int allyTankCount, int allyMageCount,
        int allyMarksmanCount, int allySupportCount) {
    public static final List<String> FEATURE_NAMES = List.of("enemy_tank_count", "enemy_mage_count",
            "enemy_marksman_count", "enemy_assassin_count", "enemy_fighter_count", "ally_tank_count",
            "ally_mage_count", "ally_marksman_count", "ally_support_count");

    public CompositionFeatures {
        if (List.of(enemyTankCount, enemyMageCount, enemyMarksmanCount, enemyAssassinCount,
                enemyFighterCount, allyTankCount, allyMageCount, allyMarksmanCount, allySupportCount)
                .stream().anyMatch(count -> count < 0)) {
            throw new IllegalArgumentException("Champion tag count는 음수일 수 없습니다.");
        }
    }

    public List<Double> values() {
        return List.of((double) enemyTankCount, (double) enemyMageCount, (double) enemyMarksmanCount,
                (double) enemyAssassinCount, (double) enemyFighterCount, (double) allyTankCount,
                (double) allyMageCount, (double) allyMarksmanCount, (double) allySupportCount);
    }
}
