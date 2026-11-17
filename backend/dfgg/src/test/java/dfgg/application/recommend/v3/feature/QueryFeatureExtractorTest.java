package dfgg.application.recommend.v3.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.PlayerState;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class QueryFeatureExtractorTest {

    private static final long YASUO = 157L;
    private static final long MALPHITE = 54L;   // TANK
    private static final long AHRI = 103L;      // MAGE
    private static final long JINX = 222L;      // MARKSMAN
    private static final long ZED = 238L;       // ASSASSIN
    private static final long THRESH = 412L;    // SUPPORT
    private static final long RAMMUS = 33L;     // TANK

    private static final long RABADON = 3089L;        // 주문력
    private static final long THORNMAIL = 3075L;      // 방어력
    private static final long KAENIC_ROOKERN = 2504L; // 마법 저항
    private static final long INFINITY_EDGE = 3031L;
    private static final long LONG_SWORD = 1036L;     // Core가 아니다
    private static final Set<Long> CORE_ITEM_IDS = Set.of(RABADON, THORNMAIL, KAENIC_ROOKERN, INFINITY_EDGE);

    private QueryFeatureExtractor extractor;
    private ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        ChampionRepository championRepository = mock(ChampionRepository.class);
        Map<Long, ChampionTag> tagByChampion = Map.of(
                MALPHITE, ChampionTag.TANK, RAMMUS, ChampionTag.TANK,
                AHRI, ChampionTag.MAGE, JINX, ChampionTag.MARKSMAN,
                ZED, ChampionTag.ASSASSIN, THRESH, ChampionTag.SUPPORT
        );
        when(championRepository.findAllWithTagsByChampionIdIn(any())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .filter(tagByChampion::containsKey)
                    .map(id -> {
                        Champion champion = mock(Champion.class);
                        when(champion.getChampionId()).thenReturn(id);
                        when(champion.getChampionTags()).thenReturn(List.of(tagByChampion.get(id)));
                        return champion;
                    })
                    .toList();
        });
        itemRepository = mock(ItemRepository.class);
        extractor = new QueryFeatureExtractor(new dfgg.application.recommend.context.CompositionFeatureCalculator(championRepository), itemRepository);
    }

    private static Item item(long itemId, int totalGold, String... tags) {
        return new Item(itemId, Map.of("en_US", "item-" + itemId), Map.of("total", totalGold), List.of(tags));
    }

    /**
     * 야스오(MID)가 인피니티 엣지를 샀고, 아군 쓰레쉬와 상대 넷(아리·제드·징크스·람머스)의 상태를 아는 요청.
     * 요청의 상대 목록에는 람머스만 있다. 값 20개가 서로 다르게 나오도록 숫자를 골랐다.
     */
    private RecommendationQuery situationQuery() {
        return new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(INFINITY_EDGE),
                List.of(THRESH), List.of(RAMMUS),
                "EMERALD", "16.17", CORE_ITEM_IDS,
                GameSituation.of(
                        new MyState(13, 7, 2, 4),
                        List.of(new PlayerState(THRESH, ChampionPosition.SUPPORT, 9, 1, 12, List.of())),
                        List.of(new PlayerState(AHRI, ChampionPosition.MID, 12, 5, 1, List.of(RABADON, LONG_SWORD)),
                                new PlayerState(ZED, ChampionPosition.JUNGLE, 10, 2, 4, List.of(THORNMAIL)),
                                new PlayerState(JINX, ChampionPosition.BOTTOM, 9, 1, 3, List.of(KAENIC_ROOKERN)),
                                new PlayerState(RAMMUS, ChampionPosition.TOP, 11, 6, 2, List.of())))
        );
    }

    private List<Item> coreItems() {
        return List.of(item(RABADON, 3600, "SpellDamage"), item(THORNMAIL, 2450, "Armor"),
                item(KAENIC_ROOKERN, 2900, "SpellBlock"), item(INFINITY_EDGE, 3450));
    }

    private FeatureVector extract(RecommendationQuery query) {
        FeatureVector vector = FeatureVector.empty();
        extractor.extract(query, vector);
        return vector;
    }

    private RecommendationQuery query(ChampionPosition position, List<Long> purchased) {
        return new RecommendationQuery(
                YASUO, position, purchased,
                List.of(JINX, THRESH, MALPHITE, AHRI),
                List.of(RAMMUS, ZED, 51L, 89L, 60L),
                "EMERALD", "16.17", Set.of()
        );
    }

    @Test
    @DisplayName("적 조합의 태그 분포를 센다 — 탱커가 많으면 관통 아이템 가치가 달라진다")
    void extract_WhenEnemiesHaveTags_CountsThem() {
        // when: 적은 람머스(TANK) + 제드(ASSASSIN)
        FeatureVector vector = extract(query(ChampionPosition.MID, List.of()));

        // then
        assertThat(vector.get(FeatureName.ENEMY_TANK_COUNT)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.ENEMY_ASSASSIN_COUNT)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.ENEMY_MAGE_COUNT)).isZero();
    }

    @Test
    @DisplayName("아군 조합의 태그 분포도 센다")
    void extract_WhenAlliesHaveTags_CountsThem() {
        // when: 아군은 징크스(MARKSMAN) + 쓰레쉬(SUPPORT) + 말파이트(TANK) + 아리(MAGE)
        FeatureVector vector = extract(query(ChampionPosition.MID, List.of()));

        // then
        assertThat(vector.get(FeatureName.ALLY_MARKSMAN_COUNT)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.ALLY_SUPPORT_COUNT)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.ALLY_TANK_COUNT)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.ALLY_MAGE_COUNT)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("태그를 모르는 챔피언은 0으로 센다 — 결측이 아니라 '해당 태그가 없다'는 관측이다")
    void extract_WhenChampionTagUnknown_CountsAsZero() {
        // when: 적 51/89/60은 태그 정보가 없다
        FeatureVector vector = extract(query(ChampionPosition.MID, List.of()));

        // then
        assertThat(vector.get(FeatureName.ENEMY_MARKSMAN_COUNT)).isZero().isNotNaN();
    }

    @Test
    @DisplayName("챔피언 태그는 요청 한 번에 한 번만 읽는다 — 아군과 적을 따로 조회하지 않는다")
    void extract_WhenCalled_ReadsChampionTagsOnce() {
        // given
        ChampionRepository championRepository = mock(ChampionRepository.class);
        when(championRepository.findAllWithTagsByChampionIdIn(any())).thenReturn(List.of());
        QueryFeatureExtractor countingExtractor = new QueryFeatureExtractor(
                new dfgg.application.recommend.context.CompositionFeatureCalculator(championRepository), itemRepository);

        // when
        countingExtractor.extract(query(ChampionPosition.MID, List.of()), FeatureVector.empty());

        // then
        verify(championRepository, times(1)).findAllWithTagsByChampionIdIn(any());
    }

    @Test
    @DisplayName("상황을 모르면 게임 상황 feature를 비워 두고 아이템을 읽지 않는다 — 빈 칸이 곧 '모름'이다")
    void extract_WhenSituationUnknown_LeavesSituationFeaturesEmptyWithoutReadingItems() {
        // given: 상황 없이 만든 요청
        RecommendationQuery unknownSituation = query(ChampionPosition.MID, List.of(INFINITY_EDGE));

        // when
        FeatureVector vector = extract(unknownSituation);

        // then
        assertThat(Arrays.stream(FeatureName.values()).skip(FeatureName.baselineExportNames().size()))
                .isNotEmpty()
                .allSatisfy(feature -> assertThat(vector.get(feature)).as(feature.name()).isNaN());
        verifyNoInteractions(itemRepository);
    }

    @Test
    @DisplayName("상황을 알면 계산한 값 20개를 각자의 feature에 쓴다 — 자리가 바뀌면 모델이 다른 값을 읽는다")
    void extract_WhenSituationKnown_WritesEachSituationValueToItsOwnFeature() {
        // given
        RecommendationQuery query = situationQuery();
        List<Item> items = coreItems();
        when(itemRepository.findAllById(any())).thenReturn(items);
        SituationFeatures expected = new SituationFeatureCalculator().calculate(
                query,
                items.stream().collect(java.util.stream.Collectors.toMap(Item::getItemId, item -> item)),
                Map.of(AHRI, List.of(ChampionTag.MAGE), ZED, List.of(ChampionTag.ASSASSIN),
                        JINX, List.of(ChampionTag.MARKSMAN), RAMMUS, List.of(ChampionTag.TANK)));

        // when
        FeatureVector vector = extract(query);

        // then: 묶음마다 값이 서로 달라야 자리가 바뀐 것을 잡는다
        assertThat(List.of(expected.enemyItems().coreGoldSum(), expected.enemyItems().spellDamageShare(),
                expected.enemyItems().armorShare(), expected.enemyItems().spellBlockShare(),
                expected.enemyKills().killSum(), expected.enemyKills().mageShare(),
                expected.enemyKills().assassinShare(), expected.enemyKills().marksmanShare(),
                expected.enemyKills().topKillerShare(),
                expected.laneOpponent().levelLead(), expected.laneOpponent().opponentKills(),
                expected.laneOpponent().coreGoldLead(),
                expected.myState().levelLead(), expected.myState().kills(),
                expected.myState().deaths(), expected.myState().assists(),
                expected.teamState().killLead(), expected.teamState().assistLead(),
                expected.teamState().levelLead(), expected.teamState().coreGoldLead()))
                .doesNotContain(Double.NaN)
                .doesNotHaveDuplicates();

        assertThat(vector.get(FeatureName.ENEMY_CORE_GOLD_SUM)).isEqualTo(expected.enemyItems().coreGoldSum());
        assertThat(vector.get(FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE)).isEqualTo(expected.enemyItems().spellDamageShare());
        assertThat(vector.get(FeatureName.ENEMY_ARMOR_GOLD_SHARE)).isEqualTo(expected.enemyItems().armorShare());
        assertThat(vector.get(FeatureName.ENEMY_SPELL_BLOCK_GOLD_SHARE)).isEqualTo(expected.enemyItems().spellBlockShare());

        assertThat(vector.get(FeatureName.ENEMY_KILL_SUM)).isEqualTo(expected.enemyKills().killSum());
        assertThat(vector.get(FeatureName.ENEMY_MAGE_KILL_SHARE)).isEqualTo(expected.enemyKills().mageShare());
        assertThat(vector.get(FeatureName.ENEMY_ASSASSIN_KILL_SHARE)).isEqualTo(expected.enemyKills().assassinShare());
        assertThat(vector.get(FeatureName.ENEMY_MARKSMAN_KILL_SHARE)).isEqualTo(expected.enemyKills().marksmanShare());
        assertThat(vector.get(FeatureName.ENEMY_TOP_KILLER_KILL_SHARE)).isEqualTo(expected.enemyKills().topKillerShare());

        assertThat(vector.get(FeatureName.LANE_LEVEL_LEAD)).isEqualTo(expected.laneOpponent().levelLead());
        assertThat(vector.get(FeatureName.LANE_OPPONENT_KILLS)).isEqualTo(expected.laneOpponent().opponentKills());
        assertThat(vector.get(FeatureName.LANE_CORE_GOLD_LEAD)).isEqualTo(expected.laneOpponent().coreGoldLead());

        assertThat(vector.get(FeatureName.MY_LEVEL_LEAD)).isEqualTo(expected.myState().levelLead());
        assertThat(vector.get(FeatureName.MY_KILLS)).isEqualTo(expected.myState().kills());
        assertThat(vector.get(FeatureName.MY_DEATHS)).isEqualTo(expected.myState().deaths());
        assertThat(vector.get(FeatureName.MY_ASSISTS)).isEqualTo(expected.myState().assists());

        assertThat(vector.get(FeatureName.TEAM_KILL_LEAD)).isEqualTo(expected.teamState().killLead());
        assertThat(vector.get(FeatureName.TEAM_ASSIST_LEAD)).isEqualTo(expected.teamState().assistLead());
        assertThat(vector.get(FeatureName.TEAM_LEVEL_LEAD)).isEqualTo(expected.teamState().levelLead());
        assertThat(vector.get(FeatureName.TEAM_CORE_GOLD_LEAD)).isEqualTo(expected.teamState().coreGoldLead());
    }

    @Test
    @DisplayName("상황에만 있는 상대 챔피언의 역할군도 읽는다 — 요청의 상대 목록에 없어도 킬 비중에 들어간다")
    void extract_WhenSituationEnemyIsNotInQueryEnemies_UsesItsTags() {
        // given: 요청의 상대는 람머스뿐이고, 상황의 상대는 아리(MAGE, 5킬)·제드(ASSASSIN, 2킬)·징크스(MARKSMAN, 1킬)·람머스(6킬)다
        when(itemRepository.findAllById(any())).thenReturn(coreItems());

        // when
        FeatureVector vector = extract(situationQuery());

        // then
        assertThat(vector.get(FeatureName.ENEMY_KILL_SUM)).isEqualTo(14.0);
        assertThat(vector.get(FeatureName.ENEMY_MAGE_KILL_SHARE)).isEqualTo(5.0 / 14);
        assertThat(vector.get(FeatureName.ENEMY_ASSASSIN_KILL_SHARE)).isEqualTo(2.0 / 14);
        assertThat(vector.get(FeatureName.ENEMY_MARKSMAN_KILL_SHARE)).isEqualTo(1.0 / 14);
    }

    @Test
    @DisplayName("상황을 알면 아이템을 한 번에 읽고, Core가 아닌 번호는 조회에 넣지 않는다")
    void extract_WhenSituationKnown_ReadsOnlyCoreItemsInOneQuery() {
        // given: 아리가 든 롱소드는 Core가 아니다
        when(itemRepository.findAllById(any())).thenReturn(coreItems());

        // when
        extract(situationQuery());

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Long>> itemIds = ArgumentCaptor.forClass(Iterable.class);
        verify(itemRepository, times(1)).findAllById(itemIds.capture());
        assertThat(itemIds.getValue())
                .containsExactlyInAnyOrder(RABADON, THORNMAIL, KAENIC_ROOKERN, INFINITY_EDGE);
    }

    @Test
    @DisplayName("상황을 알아도 조회할 Core가 없으면 아이템을 읽지 않는다")
    void extract_WhenSituationKnownButNoCoreItems_DoesNotReadItems() {
        // given: 아무도 Core를 들고 있지 않다
        RecommendationQuery noCores = new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(JINX), List.of(RAMMUS), "EMERALD", "16.17", CORE_ITEM_IDS,
                GameSituation.of(new MyState(3, 0, 0, 0), List.of(),
                        List.of(new PlayerState(AHRI, ChampionPosition.MID, 3, 0, 0, List.of(LONG_SWORD)))));

        // when
        FeatureVector vector = extract(noCores);

        // then
        verifyNoInteractions(itemRepository);
        assertThat(vector.get(FeatureName.ENEMY_CORE_GOLD_SUM)).isZero();
        assertThat(vector.get(FeatureName.MY_KILLS)).isZero();
    }

    @Test
    @DisplayName("포지션을 one-hot으로 편다 — 트리 추론이 범주형을 다루지 않기로 한 계약이다")
    void extract_WhenPositionGiven_EncodesAsOneHot() {
        // when
        FeatureVector vector = extract(query(ChampionPosition.MID, List.of()));

        // then
        assertThat(vector.get(FeatureName.POSITION_MID)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.POSITION_TOP)).isZero();
        assertThat(vector.get(FeatureName.POSITION_SUPPORT)).isZero();
    }

    @Test
    @DisplayName("서포터 포지션도 one-hot으로 잡힌다")
    void extract_WhenSupportPosition_EncodesSupportOneHot() {
        FeatureVector vector = extract(query(ChampionPosition.SUPPORT, List.of()));
        assertThat(vector.get(FeatureName.POSITION_SUPPORT)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.POSITION_MID)).isZero();
    }

    @Test
    @DisplayName("구매한 코어 개수를 남긴다 — 몇 번째 구매인지가 곧 게임 진행 단계다")
    void extract_WhenItemsPurchased_RecordsCount() {
        FeatureVector vector = extract(query(ChampionPosition.MID, List.of(6673L, 3031L)));
        assertThat(vector.get(FeatureName.PURCHASED_ITEM_COUNT)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("티어를 순서값으로 남긴다 — 상위 티어일수록 큰 값")
    void extract_WhenTierGiven_EncodesAsOrdinal() {
        FeatureVector vector = extract(query(ChampionPosition.MID, List.of()));
        assertThat(vector.get(FeatureName.TIER_ORDINAL)).isPositive();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "IRON, 1.0", "BRONZE, 2.0", "SILVER, 3.0", "GOLD, 4.0", "PLATINUM, 5.0",
            "EMERALD, 6.0", "DIAMOND, 7.0", "MASTER, 8.0", "GRANDMASTER, 9.0", "CHALLENGER, 10.0"
    })
    @DisplayName("지원 티어는 모두 정해진 순서값을 갖는다")
    void extract_WhenSupportedTier_EncodesExactOrdinal(String tier, double expected) {
        // given
        RecommendationQuery tierQuery = new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(JINX), List.of(RAMMUS), tier, "16.17", Set.of());

        // when
        FeatureVector vector = extract(tierQuery);

        // then
        assertThat(vector.get(FeatureName.TIER_ORDINAL)).isEqualTo(expected);
    }

    @Test
    @DisplayName("소문자로 와도 같은 순서값이다")
    void extract_WhenTierLowerCase_EncodesSameOrdinal() {
        // given
        RecommendationQuery lowerCase = new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(JINX), List.of(RAMMUS), "challenger", "16.17", Set.of());

        // when & then
        assertThat(extract(lowerCase).get(FeatureName.TIER_ORDINAL)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("알 수 없는 티어는 NaN이다 — 임의의 값을 지어내지 않는다")
    void extract_WhenTierUnknown_LeavesNaN() {
        RecommendationQuery unknownTier = new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(JINX), List.of(RAMMUS), "UNRANKED", "16.17", Set.of());

        FeatureVector vector = extract(unknownTier);

        assertThat(vector.get(FeatureName.TIER_ORDINAL)).isNaN();
    }
}
