package dfgg.application.recommend.v3.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.PlayerState;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SituationFeatureCalculatorTest {

    private static final long YASUO = 157L;
    private static final long JINX = 222L;
    private static final long AHRI = 103L;
    private static final long MALPHITE = 54L;
    private static final long ZED = 238L;
    private static final long CAITLYN = 51L;
    private static final long UNTAGGED_CHAMPION = 999L;   // 역할군 목록에 없음

    private static final long AP_ITEM = 1L;          // 3000, SpellDamage
    private static final long ARMOR_ITEM = 2L;       // 2000, Armor
    private static final long MR_ITEM = 3L;          // 1000, SpellBlock
    private static final long AD_ITEM = 4L;          // 4000, 세 태그 없음
    private static final long AP_ARMOR_ITEM = 5L;    // 2000, SpellDamage + Armor
    private static final long PRICELESS_ITEM = 6L;   // 가격 없음, SpellDamage
    private static final long UNLISTED_ITEM = 99L;   // 아이템 목록에 없음

    private static final Map<Long, Item> ITEMS = Map.of(
            AP_ITEM, item(AP_ITEM, 3000, "SpellDamage"),
            ARMOR_ITEM, item(ARMOR_ITEM, 2000, "Armor"),
            MR_ITEM, item(MR_ITEM, 1000, "SpellBlock"),
            AD_ITEM, item(AD_ITEM, 4000, "Damage"),
            AP_ARMOR_ITEM, item(AP_ARMOR_ITEM, 2000, "SpellDamage", "Armor"),
            PRICELESS_ITEM, new Item(PRICELESS_ITEM, Map.of("ko-KR", "가격 없는 아이템"), List.of("SpellDamage"))
    );
    private static final Map<Long, List<ChampionTag>> NO_CHAMPION_TAGS = Map.of();
    private static final Map<Long, List<ChampionTag>> CHAMPION_TAGS = Map.of(
            AHRI, List.of(ChampionTag.MAGE, ChampionTag.ASSASSIN),
            ZED, List.of(ChampionTag.ASSASSIN),
            CAITLYN, List.of(ChampionTag.MARKSMAN),
            MALPHITE, List.of(ChampionTag.TANK),
            JINX, List.of(ChampionTag.MARKSMAN)
    );

    private final SituationFeatureCalculator calculator = new SituationFeatureCalculator();

    private static Item item(long itemId, int totalGold, String... tags) {
        return new Item(itemId, Map.of("ko-KR", "아이템 " + itemId), Map.of("total", totalGold), List.of(tags));
    }

    private static PlayerState playerHolding(long championId, Long... coreItemIds) {
        return new PlayerState(championId, null, 1, 0, 0, List.of(coreItemIds));
    }

    private static PlayerState playerWithKills(long championId, int kills) {
        return new PlayerState(championId, null, 1, kills, 0, List.of());
    }

    private static RecommendationQuery queryWith(GameSituation situation) {
        return new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(), List.of(JINX), List.of(AHRI, MALPHITE),
                "EMERALD", "16.17", Set.of(), situation);
    }

    private static GameSituation situationOf(List<PlayerState> allies, List<PlayerState> enemies) {
        return GameSituation.of(new MyState(1, 0, 0, 0), allies, enemies);
    }

    @Test
    @DisplayName("상대의 아이템 — 상대가 든 Core의 가격 합과, 그중 주문력·방어력·마법 저항 아이템의 가격 비중을 낸다")
    void calculate_WhenEnemiesHoldCores_ReturnsGoldSumAndShares() {
        // given: 상대 Core 가격 합 10000 = 주문력 3000 + 방어력 2000 + 마법 저항 1000 + 그 밖 4000
        GameSituation situation = situationOf(List.of(), List.of(
                playerHolding(AHRI, AP_ITEM, AD_ITEM),
                playerHolding(MALPHITE, ARMOR_ITEM, MR_ITEM)));

        // when
        EnemyItemFeatures enemyItems = calculator.calculate(queryWith(situation), ITEMS, NO_CHAMPION_TAGS).enemyItems();

        // then
        assertThat(enemyItems.coreGoldSum()).isEqualTo(10000.0);
        assertThat(enemyItems.spellDamageShare()).isCloseTo(0.3, within(1e-9));
        assertThat(enemyItems.armorShare()).isCloseTo(0.2, within(1e-9));
        assertThat(enemyItems.spellBlockShare()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    @DisplayName("상대의 아이템 — 태그가 둘인 아이템은 두 비중에 모두 들어간다")
    void calculate_WhenItemHasTwoTags_CountsItInBothShares() {
        // given: 가격 합 4000 = 주문력·방어력을 함께 가진 2000 + 방어력 2000
        GameSituation situation = situationOf(List.of(), List.of(
                playerHolding(MALPHITE, AP_ARMOR_ITEM, ARMOR_ITEM)));

        // when
        EnemyItemFeatures enemyItems = calculator.calculate(queryWith(situation), ITEMS, NO_CHAMPION_TAGS).enemyItems();

        // then
        assertThat(enemyItems.spellDamageShare()).isCloseTo(0.5, within(1e-9));
        assertThat(enemyItems.armorShare()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("상대의 아이템 — 아이템 목록에 없는 번호와 가격이 없는 아이템은 어디에도 더하지 않는다")
    void calculate_WhenItemIsUnlistedOrPriceless_IgnoresIt() {
        // given: 셀 수 있는 것은 주문력 3000과 그 밖 4000뿐이다
        GameSituation situation = situationOf(List.of(), List.of(
                playerHolding(AHRI, AP_ITEM, UNLISTED_ITEM, PRICELESS_ITEM),
                playerHolding(MALPHITE, AD_ITEM)));

        // when
        EnemyItemFeatures enemyItems = calculator.calculate(queryWith(situation), ITEMS, NO_CHAMPION_TAGS).enemyItems();

        // then
        assertThat(enemyItems.coreGoldSum()).isEqualTo(7000.0);
        assertThat(enemyItems.spellDamageShare()).isCloseTo(3000.0 / 7000.0, within(1e-9));
    }

    @Test
    @DisplayName("상대의 아이템 — 아군이 든 Core는 상대의 아이템 값에 섞이지 않는다")
    void calculate_WhenAlliesHoldCores_DoesNotCountThemAsEnemyItems() {
        // given
        GameSituation situation = situationOf(
                List.of(playerHolding(JINX, AP_ITEM)),
                List.of(playerHolding(MALPHITE, ARMOR_ITEM)));

        // when
        EnemyItemFeatures enemyItems = calculator.calculate(queryWith(situation), ITEMS, NO_CHAMPION_TAGS).enemyItems();

        // then
        assertThat(enemyItems.coreGoldSum()).isEqualTo(2000.0);
        assertThat(enemyItems.spellDamageShare()).isZero();
    }

    @Test
    @DisplayName("상대의 아이템 — 상대가 Core를 하나도 안 들었으면 가격 합은 0이고 비중 셋만 비운다")
    void calculate_WhenEnemiesHoldNoCores_ReturnsZeroSumAndNaNShares() {
        // given
        GameSituation situation = situationOf(List.of(), List.of(playerHolding(AHRI), playerHolding(MALPHITE)));

        // when
        EnemyItemFeatures enemyItems = calculator.calculate(queryWith(situation), ITEMS, NO_CHAMPION_TAGS).enemyItems();

        // then
        assertThat(enemyItems.coreGoldSum()).isZero();
        assertThat(enemyItems.spellDamageShare()).isNaN();
        assertThat(enemyItems.armorShare()).isNaN();
        assertThat(enemyItems.spellBlockShare()).isNaN();
    }

    @Test
    @DisplayName("상대의 아이템 — 상황을 모르면 네 값을 모두 비운다")
    void calculate_WhenSituationUnknown_ReturnsNaNEnemyItems() {
        // when
        EnemyItemFeatures enemyItems = calculator
                .calculate(queryWith(GameSituation.unknown()), ITEMS, NO_CHAMPION_TAGS).enemyItems();

        // then
        assertThat(enemyItems.coreGoldSum()).isNaN();
        assertThat(enemyItems.spellDamageShare()).isNaN();
        assertThat(enemyItems.armorShare()).isNaN();
        assertThat(enemyItems.spellBlockShare()).isNaN();
    }

    @Test
    @DisplayName("상대 중 누가 컸나 — 상대 킬 합과, 그중 메이지·암살자·원딜이 올린 킬의 비중, 최다 킬 선수의 비중을 낸다")
    void calculate_WhenEnemiesHaveKills_ReturnsKillSumAndShares() {
        // given: 상대 킬 합 10 = 암살자 5 + 원딜 3 + 탱커 2
        GameSituation situation = situationOf(List.of(), List.of(
                playerWithKills(ZED, 5), playerWithKills(CAITLYN, 3), playerWithKills(MALPHITE, 2)));

        // when
        EnemyKillFeatures enemyKills = calculator.calculate(queryWith(situation), ITEMS, CHAMPION_TAGS).enemyKills();

        // then
        assertThat(enemyKills.killSum()).isEqualTo(10.0);
        assertThat(enemyKills.mageShare()).isZero();
        assertThat(enemyKills.assassinShare()).isCloseTo(0.5, within(1e-9));
        assertThat(enemyKills.marksmanShare()).isCloseTo(0.3, within(1e-9));
        assertThat(enemyKills.topKillerShare()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("상대 중 누가 컸나 — 역할군이 둘인 챔피언의 킬은 두 비중에 모두 들어간다")
    void calculate_WhenEnemyChampionHasTwoRoles_CountsKillsInBothShares() {
        // given: 메이지이자 암살자인 챔피언 4킬 + 암살자 4킬 + 탱커 2킬
        GameSituation situation = situationOf(List.of(), List.of(
                playerWithKills(AHRI, 4), playerWithKills(ZED, 4), playerWithKills(MALPHITE, 2)));

        // when
        EnemyKillFeatures enemyKills = calculator.calculate(queryWith(situation), ITEMS, CHAMPION_TAGS).enemyKills();

        // then: 두 비중의 합이 1을 넘는다
        assertThat(enemyKills.mageShare()).isCloseTo(0.4, within(1e-9));
        assertThat(enemyKills.assassinShare()).isCloseTo(0.8, within(1e-9));
    }

    @Test
    @DisplayName("상대 중 누가 컸나 — 역할군을 모르는 챔피언의 킬은 킬 합과 최다 킬에는 들어가고 역할군 비중에는 들어가지 않는다")
    void calculate_WhenEnemyChampionRoleIsUnknown_CountsKillsOnlyInSum() {
        // given
        GameSituation situation = situationOf(List.of(), List.of(
                playerWithKills(UNTAGGED_CHAMPION, 6), playerWithKills(ZED, 4)));

        // when
        EnemyKillFeatures enemyKills = calculator.calculate(queryWith(situation), ITEMS, CHAMPION_TAGS).enemyKills();

        // then
        assertThat(enemyKills.killSum()).isEqualTo(10.0);
        assertThat(enemyKills.assassinShare()).isCloseTo(0.4, within(1e-9));
        assertThat(enemyKills.mageShare()).isZero();
        assertThat(enemyKills.marksmanShare()).isZero();
        assertThat(enemyKills.topKillerShare()).isCloseTo(0.6, within(1e-9));
    }

    @Test
    @DisplayName("상대 중 누가 컸나 — 아군의 킬은 상대 킬에 섞이지 않는다")
    void calculate_WhenAlliesHaveKills_DoesNotCountThemAsEnemyKills() {
        // given
        GameSituation situation = situationOf(
                List.of(playerWithKills(JINX, 7)),
                List.of(playerWithKills(ZED, 2)));

        // when
        EnemyKillFeatures enemyKills = calculator.calculate(queryWith(situation), ITEMS, CHAMPION_TAGS).enemyKills();

        // then
        assertThat(enemyKills.killSum()).isEqualTo(2.0);
        assertThat(enemyKills.marksmanShare()).isZero();
    }

    @Test
    @DisplayName("상대 중 누가 컸나 — 상대 킬이 0이면 킬 합은 0이고 비중 넷만 비운다. 상대의 아이템 값은 그대로 나온다")
    void calculate_WhenEnemiesHaveNoKills_ReturnsZeroSumAndNaNShares() {
        // given: 킬은 없지만 Core는 들고 있다
        GameSituation situation = situationOf(List.of(), List.of(playerHolding(AHRI, AP_ITEM)));

        // when
        SituationFeatures features = calculator.calculate(queryWith(situation), ITEMS, CHAMPION_TAGS);

        // then
        EnemyKillFeatures enemyKills = features.enemyKills();
        assertThat(enemyKills.killSum()).isZero();
        assertThat(enemyKills.mageShare()).isNaN();
        assertThat(enemyKills.assassinShare()).isNaN();
        assertThat(enemyKills.marksmanShare()).isNaN();
        assertThat(enemyKills.topKillerShare()).isNaN();
        assertThat(features.enemyItems().coreGoldSum()).isEqualTo(3000.0);
        assertThat(features.enemyItems().spellDamageShare()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("상대 중 누가 컸나 — 상황을 모르면 다섯 값을 모두 비운다")
    void calculate_WhenSituationUnknown_ReturnsNaNEnemyKills() {
        // when
        EnemyKillFeatures enemyKills = calculator
                .calculate(queryWith(GameSituation.unknown()), ITEMS, CHAMPION_TAGS).enemyKills();

        // then
        assertThat(enemyKills.killSum()).isNaN();
        assertThat(enemyKills.mageShare()).isNaN();
        assertThat(enemyKills.assassinShare()).isNaN();
        assertThat(enemyKills.marksmanShare()).isNaN();
        assertThat(enemyKills.topKillerShare()).isNaN();
    }
}
