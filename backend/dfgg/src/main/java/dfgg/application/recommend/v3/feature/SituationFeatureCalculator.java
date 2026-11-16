package dfgg.application.recommend.v3.feature;

import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.PlayerState;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 요청에 실린 상황을 상황 값으로 바꾼다. DB를 보지 않고, 필요한 것을 전부 넘겨받아 계산만 한다 —
 * 학습 데이터 내보내기와 서빙이 같은 계산을 타고, 아이템·챔피언 조회는 부르는 쪽이 요청당 한 번만 한다.
 *
 * <p>{@code items}에 없는 번호와 가격이 없는 아이템은 어디에도 더하지 않는다.
 */
public class SituationFeatureCalculator {

    private static final String SPELL_DAMAGE_TAG = "SpellDamage";
    private static final String ARMOR_TAG = "Armor";
    private static final String SPELL_BLOCK_TAG = "SpellBlock";

    public SituationFeatures calculate(
            RecommendationQuery query,
            Map<Long, Item> items,
            Map<Long, List<ChampionTag>> championTags
    ) {
        if (!(query.situation() instanceof GameSituation.Known situation)) {
            return SituationFeatures.unknown();
        }
        return new SituationFeatures(
                enemyItems(situation.enemies(), items),
                enemyKills(situation.enemies(), championTags),
                laneOpponent(query, situation, items),
                myState(situation.me(), situation.enemies()),
                teamState(query, situation, items)
        );
    }

    private EnemyItemFeatures enemyItems(List<PlayerState> enemies, Map<Long, Item> items) {
        List<Item> cores = pricedItems(coreItemIdsOf(enemies), items);
        double goldSum = goldSum(cores);
        return new EnemyItemFeatures(
                goldSum,
                share(goldSum(tagged(cores, SPELL_DAMAGE_TAG)), goldSum),
                share(goldSum(tagged(cores, ARMOR_TAG)), goldSum),
                share(goldSum(tagged(cores, SPELL_BLOCK_TAG)), goldSum)
        );
    }

    private EnemyKillFeatures enemyKills(List<PlayerState> enemies, Map<Long, List<ChampionTag>> championTags) {
        double killSum = killSum(enemies);
        return new EnemyKillFeatures(
                killSum,
                share(killSum(withRole(enemies, ChampionTag.MAGE, championTags)), killSum),
                share(killSum(withRole(enemies, ChampionTag.ASSASSIN, championTags)), killSum),
                share(killSum(withRole(enemies, ChampionTag.MARKSMAN, championTags)), killSum),
                share(mostKills(enemies), killSum)
        );
    }

    /** 역할군을 모르는 챔피언은 어느 역할군에도 넣지 않는다. */
    private List<PlayerState> withRole(
            List<PlayerState> players,
            ChampionTag role,
            Map<Long, List<ChampionTag>> championTags
    ) {
        return players.stream()
                .filter(player -> championTags.getOrDefault(player.championId(), List.of()).contains(role))
                .toList();
    }

    private double killSum(List<PlayerState> players) {
        return players.stream()
                .mapToInt(PlayerState::kills)
                .sum();
    }

    private double mostKills(List<PlayerState> players) {
        return players.stream()
                .mapToInt(PlayerState::kills)
                .max()
                .orElse(0);
    }

    private LaneOpponentFeatures laneOpponent(
            RecommendationQuery query,
            GameSituation.Known situation,
            Map<Long, Item> items
    ) {
        List<PlayerState> opponents = situation.enemies().stream()
                .filter(enemy -> enemy.playsAt(query.position()))
                .toList();
        if (opponents.size() != 1) {
            return LaneOpponentFeatures.unknown();
        }
        PlayerState opponent = opponents.get(0);
        return new LaneOpponentFeatures(
                situation.me().level() - opponent.level(),
                opponent.kills(),
                coreGoldSum(query.purchasedItemIds(), items) - coreGoldSum(opponent.coreItemIds(), items)
        );
    }

    private MyStateFeatures myState(MyState me, List<PlayerState> enemies) {
        return new MyStateFeatures(
                me.level() - averageLevel(enemies),
                me.kills(),
                me.deaths(),
                me.assists()
        );
    }

    /** 우리 팀은 나와 아군이다. 내 Core는 요청의 {@code purchasedItemIds}에서 읽는다. */
    private TeamStateFeatures teamState(
            RecommendationQuery query,
            GameSituation.Known situation,
            Map<Long, Item> items
    ) {
        MyState me = situation.me();
        List<PlayerState> allies = situation.allies();
        List<PlayerState> enemies = situation.enemies();
        double myTeamCoreGold = coreGoldSum(query.purchasedItemIds(), items)
                + coreGoldSum(coreItemIdsOf(allies), items);
        return new TeamStateFeatures(
                me.kills() + killSum(allies) - killSum(enemies),
                me.assists() + assistSum(allies) - assistSum(enemies),
                myTeamAverageLevel(me, allies) - averageLevel(enemies),
                myTeamCoreGold - coreGoldSum(coreItemIdsOf(enemies), items)
        );
    }

    private double assistSum(List<PlayerState> players) {
        return players.stream()
                .mapToInt(PlayerState::assists)
                .sum();
    }

    /** 우리 팀에는 내가 늘 있어 평균을 낼 선수가 없는 경우가 없다. */
    private double myTeamAverageLevel(MyState me, List<PlayerState> allies) {
        double levelSum = me.level() + allies.stream()
                .mapToInt(PlayerState::level)
                .sum();
        return levelSum / (allies.size() + 1);
    }

    /** 실린 선수 수로 나눈다. 평균을 낼 선수가 없으면 비운다. */
    private double averageLevel(List<PlayerState> players) {
        return players.stream()
                .mapToInt(PlayerState::level)
                .average()
                .orElse(Double.NaN);
    }

    private double coreGoldSum(List<Long> coreItemIds, Map<Long, Item> items) {
        return goldSum(pricedItems(coreItemIds, items));
    }

    private List<Long> coreItemIdsOf(List<PlayerState> players) {
        return players.stream()
                .flatMap(player -> player.coreItemIds().stream())
                .toList();
    }

    /** 가격을 아는 아이템만 남긴다. 같은 번호가 여러 번 오면 그만큼 남긴다. */
    private List<Item> pricedItems(List<Long> itemIds, Map<Long, Item> items) {
        return itemIds.stream()
                .map(items::get)
                .filter(Objects::nonNull)
                .filter(item -> item.totalGold().isPresent())
                .toList();
    }

    private List<Item> tagged(List<Item> items, String tag) {
        return items.stream()
                .filter(item -> item.hasTag(tag))
                .toList();
    }

    private double goldSum(List<Item> pricedItems) {
        return pricedItems.stream()
                .mapToInt(item -> item.totalGold().getAsInt())
                .sum();
    }

    /** 분모가 0이면 비중은 뜻이 없어 비운다. */
    private double share(double part, double whole) {
        if (whole == 0) {
            return Double.NaN;
        }
        return part / whole;
    }
}
