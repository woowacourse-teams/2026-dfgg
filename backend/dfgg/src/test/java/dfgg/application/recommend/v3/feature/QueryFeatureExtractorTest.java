package dfgg.application.recommend.v3.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryFeatureExtractorTest {

    private static final long YASUO = 157L;
    private static final long MALPHITE = 54L;   // TANK
    private static final long AHRI = 103L;      // MAGE
    private static final long JINX = 222L;      // MARKSMAN
    private static final long ZED = 238L;       // ASSASSIN
    private static final long THRESH = 412L;    // SUPPORT
    private static final long RAMMUS = 33L;     // TANK

    private QueryFeatureExtractor extractor;

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
        extractor = new QueryFeatureExtractor(championRepository);
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
                "EMERALD", "16.17"
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

    @Test
    @DisplayName("알 수 없는 티어는 NaN이다 — 임의의 값을 지어내지 않는다")
    void extract_WhenTierUnknown_LeavesNaN() {
        RecommendationQuery unknownTier = new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(JINX), List.of(RAMMUS), "UNRANKED", "16.17");

        FeatureVector vector = extract(unknownTier);

        assertThat(vector.get(FeatureName.TIER_ORDINAL)).isNaN();
    }
}
