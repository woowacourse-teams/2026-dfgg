package dfgg.application.recommend.v3.feature;

import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.PlayerState;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.context.CompositionFeatures;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 후보와 무관하게 질의 자체에서 나오는 feature — 양 팀 조합 구성, 게임 상태, 게임 상황.
 * <p>
 * 같은 질의의 모든 후보가 같은 값을 갖는다. 랭킹은 후보 간 비교이므로 이 값들만으로는
 * 순위가 갈리지 않지만, 트리가 다른 feature와 조합해 분기하는 데 쓴다 —
 * "적에 탱커가 많을 때는 관통 아이템의 counter lift를 더 믿는다" 같은 규칙이 그 예다.
 * <p>
 * 범주형은 전부 one-hot으로 편다.
 */
@Component
public class QueryFeatureExtractor {

    /** 저티어→고티어 순서값. */
    private static final Map<String, Double> TIER_ORDINALS = Map.ofEntries(
            Map.entry("IRON", 1.0), Map.entry("BRONZE", 2.0), Map.entry("SILVER", 3.0),
            Map.entry("GOLD", 4.0), Map.entry("PLATINUM", 5.0), Map.entry("EMERALD", 6.0),
            Map.entry("DIAMOND", 7.0), Map.entry("MASTER", 8.0), Map.entry("GRANDMASTER", 9.0),
            Map.entry("CHALLENGER", 10.0)
    );

    private static final Map<ChampionPosition, FeatureName> POSITION_FEATURES = Map.of(
            ChampionPosition.TOP, FeatureName.POSITION_TOP,
            ChampionPosition.JUNGLE, FeatureName.POSITION_JUNGLE,
            ChampionPosition.MID, FeatureName.POSITION_MID,
            ChampionPosition.BOTTOM, FeatureName.POSITION_BOTTOM,
            ChampionPosition.SUPPORT, FeatureName.POSITION_SUPPORT
    );

    private final CompositionFeatureCalculator compositions;
    private final ItemRepository items;
    /** 상태도 의존성도 없는 계산 객체라 빈으로 두지 않고 직접 갖는다. 필요한 조회는 여기서 해서 넘긴다. */
    private final SituationFeatureCalculator situations = new SituationFeatureCalculator();

    public QueryFeatureExtractor(CompositionFeatureCalculator compositions, ItemRepository items) {
        this.compositions = compositions;
        this.items = items;
    }

    public void extract(RecommendationQuery query, FeatureVector vector) {
        // 챔피언 태그는 요청당 한 번만 읽어 조합 계산과 상황 계산이 같이 쓴다.
        Map<Long, List<ChampionTag>> championTags = compositions.readTags(championIdsOf(query));
        setTeamComposition(query, championTags, vector);
        setGameState(query, vector);
        setGameSituation(query, championTags, vector);
    }

    /** 요청의 아군·상대에 더해, 상황에 실린 상대도 읽는다 — 상대 역할군별 킬 비중에 쓴다. */
    private Set<Long> championIdsOf(RecommendationQuery query) {
        Set<Long> championIds = new LinkedHashSet<>(query.allyChampionIds());
        championIds.addAll(query.enemyChampionIds());
        if (query.situation() instanceof GameSituation.Known situation) {
            situation.enemies().forEach(enemy -> championIds.add(enemy.championId()));
        }
        return championIds;
    }

    private void setTeamComposition(
            RecommendationQuery query, Map<Long, List<ChampionTag>> championTags, FeatureVector vector) {
        var composition = compositions.calculate(query.allyChampionIds(), query.enemyChampionIds(), championTags);
        var values = composition.values();
        for (int i = 0; i < CompositionFeatures.FEATURE_NAMES.size(); i++) {
            var name = CompositionFeatures.FEATURE_NAMES.get(i);
            vector.set(FeatureName.valueOf(name.toUpperCase(Locale.ROOT)), values.get(i));
        }
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

    /**
     * 상황을 모르면 아이템을 읽지 않고 칸도 건드리지 않는다. 비어 있는 칸(NaN)이 곧 "모름"이다 —
     * 0을 넣으면 모델이 "킬이 없다", "상대가 아무것도 안 샀다"로 읽는다.
     */
    private void setGameSituation(
            RecommendationQuery query, Map<Long, List<ChampionTag>> championTags, FeatureVector vector) {
        if (!(query.situation() instanceof GameSituation.Known situation)) {
            return;
        }
        SituationFeatures features = situations.calculate(query, readCoreItems(query, situation), championTags);

        EnemyItemFeatures enemyItems = features.enemyItems();
        vector.set(FeatureName.ENEMY_CORE_GOLD_SUM, enemyItems.coreGoldSum());
        vector.set(FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE, enemyItems.spellDamageShare());
        vector.set(FeatureName.ENEMY_ARMOR_GOLD_SHARE, enemyItems.armorShare());
        vector.set(FeatureName.ENEMY_SPELL_BLOCK_GOLD_SHARE, enemyItems.spellBlockShare());

        EnemyKillFeatures enemyKills = features.enemyKills();
        vector.set(FeatureName.ENEMY_KILL_SUM, enemyKills.killSum());
        vector.set(FeatureName.ENEMY_MAGE_KILL_SHARE, enemyKills.mageShare());
        vector.set(FeatureName.ENEMY_ASSASSIN_KILL_SHARE, enemyKills.assassinShare());
        vector.set(FeatureName.ENEMY_MARKSMAN_KILL_SHARE, enemyKills.marksmanShare());
        vector.set(FeatureName.ENEMY_TOP_KILLER_KILL_SHARE, enemyKills.topKillerShare());

        LaneOpponentFeatures laneOpponent = features.laneOpponent();
        vector.set(FeatureName.LANE_LEVEL_LEAD, laneOpponent.levelLead());
        vector.set(FeatureName.LANE_OPPONENT_KILLS, laneOpponent.opponentKills());
        vector.set(FeatureName.LANE_CORE_GOLD_LEAD, laneOpponent.coreGoldLead());

        MyStateFeatures myState = features.myState();
        vector.set(FeatureName.MY_LEVEL_LEAD, myState.levelLead());
        vector.set(FeatureName.MY_KILLS, myState.kills());
        vector.set(FeatureName.MY_DEATHS, myState.deaths());
        vector.set(FeatureName.MY_ASSISTS, myState.assists());

        TeamStateFeatures teamState = features.teamState();
        vector.set(FeatureName.TEAM_KILL_LEAD, teamState.killLead());
        vector.set(FeatureName.TEAM_ASSIST_LEAD, teamState.assistLead());
        vector.set(FeatureName.TEAM_LEVEL_LEAD, teamState.levelLead());
        vector.set(FeatureName.TEAM_CORE_GOLD_LEAD, teamState.coreGoldLead());
    }

    /**
     * 내가 산 아이템과 상황의 아군·상대가 든 아이템 중 Core만 한 번에 읽는다.
     * 클라이언트는 들고 있는 번호를 그대로 보내므로 Core가 아닌 번호는 여기서 걸러 조회에 넣지 않는다.
     */
    private Map<Long, Item> readCoreItems(RecommendationQuery query, GameSituation.Known situation) {
        Set<Long> coreItemIds = Stream.of(
                        query.purchasedItemIds().stream(),
                        heldItemIdsOf(situation.allies()),
                        heldItemIdsOf(situation.enemies()))
                .flatMap(Function.identity())
                .filter(query.coreItemIds()::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (coreItemIds.isEmpty()) {
            return Map.of();
        }
        return items.findAllById(coreItemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity()));
    }

    private Stream<Long> heldItemIdsOf(List<PlayerState> players) {
        return players.stream().flatMap(player -> player.coreItemIds().stream());
    }

}
