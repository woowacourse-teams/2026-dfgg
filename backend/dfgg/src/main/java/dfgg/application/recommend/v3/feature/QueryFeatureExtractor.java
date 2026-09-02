package dfgg.application.recommend.v3.feature;

import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 후보와 무관하게 질의 자체에서 나오는 feature — 양 팀 조합 구성과 게임 상태.
 * <p>
 * 같은 질의의 모든 후보가 같은 값을 갖는다. 랭킹은 후보 간 비교이므로 이 값들만으로는
 * 순위가 갈리지 않지만, 트리가 다른 feature와 조합해 분기하는 데 쓴다 —
 * "적에 탱커가 많을 때는 관통 아이템의 counter lift를 더 믿는다" 같은 규칙이 그 예다.
 * <p>
 * 범주형은 전부 one-hot으로 편다.
 */
@Component
public class QueryFeatureExtractor {

    /** 저티어→고티어 순서값. 데이터가 PLATINUM에 90% 편중돼 분산은 작지만 그대로 넘긴다. */
    private static final Map<String, Double> TIER_ORDINALS = Map.of(
            "IRON", 1.0, "BRONZE", 2.0, "SILVER", 3.0, "GOLD", 4.0,
            "PLATINUM", 5.0, "EMERALD", 6.0, "DIAMOND", 7.0
    );

    private static final Map<ChampionPosition, FeatureName> POSITION_FEATURES = Map.of(
            ChampionPosition.TOP, FeatureName.POSITION_TOP,
            ChampionPosition.JUNGLE, FeatureName.POSITION_JUNGLE,
            ChampionPosition.MID, FeatureName.POSITION_MID,
            ChampionPosition.BOTTOM, FeatureName.POSITION_BOTTOM,
            ChampionPosition.SUPPORT, FeatureName.POSITION_SUPPORT
    );

    private final ChampionRepository championRepository;

    public QueryFeatureExtractor(ChampionRepository championRepository) {
        this.championRepository = championRepository;
    }

    public void extract(RecommendationQuery query, FeatureVector vector) {
        setTeamComposition(query, vector);
        setGameState(query, vector);
    }

    /**
     * 태그를 모르는 챔피언은 어느 칸에도 세지 않아 결과적으로 0이 된다. 0으로 두는 게 맞다 —
     * "그 태그의 챔피언이 없다"는 관측이지 결측이 아니다.
     */
    private void setTeamComposition(RecommendationQuery query, FeatureVector vector) {
        Map<ChampionTag, Long> enemyTags = countTags(query.enemyChampionIds());
        vector.set(FeatureName.ENEMY_TANK_COUNT, count(enemyTags, ChampionTag.TANK));
        vector.set(FeatureName.ENEMY_MAGE_COUNT, count(enemyTags, ChampionTag.MAGE));
        vector.set(FeatureName.ENEMY_MARKSMAN_COUNT, count(enemyTags, ChampionTag.MARKSMAN));
        vector.set(FeatureName.ENEMY_ASSASSIN_COUNT, count(enemyTags, ChampionTag.ASSASSIN));
        vector.set(FeatureName.ENEMY_FIGHTER_COUNT, count(enemyTags, ChampionTag.FIGHTER));

        Map<ChampionTag, Long> allyTags = countTags(query.allyChampionIds());
        vector.set(FeatureName.ALLY_TANK_COUNT, count(allyTags, ChampionTag.TANK));
        vector.set(FeatureName.ALLY_MAGE_COUNT, count(allyTags, ChampionTag.MAGE));
        vector.set(FeatureName.ALLY_MARKSMAN_COUNT, count(allyTags, ChampionTag.MARKSMAN));
        vector.set(FeatureName.ALLY_SUPPORT_COUNT, count(allyTags, ChampionTag.SUPPORT));
    }

    private void setGameState(RecommendationQuery query, FeatureVector vector) {
        vector.set(FeatureName.PURCHASED_ITEM_COUNT, query.purchasedItemCount());

        POSITION_FEATURES.forEach((position, feature) ->
                vector.set(feature, position == query.position() ? 1.0 : 0.0));

        // 알 수 없는 티어는 NaN으로 남긴다. 중간값을 지어내면 모델이 그걸 실제 관측으로 읽는다.
        Double ordinal = query.tier() == null
                ? null
                : TIER_ORDINALS.get(query.tier().toUpperCase(Locale.ROOT));
        if (ordinal != null) {
            vector.set(FeatureName.TIER_ORDINAL, ordinal);
        }
    }

    private Map<ChampionTag, Long> countTags(List<Long> championIds) {
        if (championIds.isEmpty()) {
            return Map.of();
        }
        return championRepository.findAllWithTagsByChampionIdIn(championIds).stream()
                .map(Champion::getChampionTags)
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.groupingBy(tag -> tag, java.util.stream.Collectors.counting()));
    }

    private double count(Map<ChampionTag, Long> tagCounts, ChampionTag tag) {
        return tagCounts.getOrDefault(tag, 0L);
    }
}
