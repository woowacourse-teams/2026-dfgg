package dfgg.application.recommend.v3.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파이프라인이 후보마다 다른 상황 값을 채우는지 본다. 질의 단위 값과 통계 값은 다른 테스트가 보므로 여기서는 대역을 쓴다.
 */
class FeatureExtractionPipelineSituationMatchTest {

    private static final long VOID_STAFF = 3135L;
    private static final long RABADON = 3089L;

    private final CandidateFeatureExtractor candidateFeatureExtractor = mock(CandidateFeatureExtractor.class);
    private final StatsFeatureExtractor statsFeatureExtractor = mock(StatsFeatureExtractor.class);
    private final QueryFeatureExtractor queryFeatureExtractor = mock(QueryFeatureExtractor.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);

    private final FeatureExtractionPipeline pipeline = new FeatureExtractionPipeline(
            candidateFeatureExtractor, statsFeatureExtractor, queryFeatureExtractor, itemRepository);

    private final CandidateUnion union = new CandidateUnion(List.of(
            new ItemCandidate(VOID_STAFF, Map.of()), new ItemCandidate(RABADON, Map.of())));

    private static RecommendationQuery query(GameSituation situation) {
        return new RecommendationQuery(103L, ChampionPosition.MID, List.of(), List.of(1L, 2L, 3L, 4L),
                List.of(5L, 6L, 7L, 8L, 9L), "EMERALD", "16.22", Set.of(VOID_STAFF, RABADON), situation);
    }

    private static Item item(long itemId, String... tags) {
        return new Item(itemId, Map.of("ko-KR", "아이템 " + itemId), List.of(tags));
    }

    /** 질의 단위 추출기가 상황을 알 때처럼 상대 마법 저항 비중을 채운다. */
    private void givenEnemySpellBlockShare(double share) {
        doAnswer(invocation -> {
            FeatureVector vector = invocation.getArgument(1);
            vector.set(FeatureName.ENEMY_SPELL_BLOCK_GOLD_SHARE, share);
            return null;
        }).when(queryFeatureExtractor).extract(any(), any());
    }

    private static double valueOf(List<CandidateFeatures> extracted, long itemId, FeatureName name) {
        return extracted.stream()
                .filter(features -> features.itemId() == itemId)
                .findFirst()
                .orElseThrow()
                .vector()
                .get(name);
    }

    @Test
    @DisplayName("상황을 알면 같은 요청의 후보끼리 상황 맞춤 값이 다르다 — 상대가 마법 저항을 쌓았으면 마법 관통 후보만 값을 받는다")
    void extract_WhenSituationKnown_CandidatesGetDifferentSituationMatchValues() {
        // given
        givenEnemySpellBlockShare(0.4);
        when(itemRepository.findAllById(any())).thenReturn(List.of(
                item(VOID_STAFF, "SpellDamage", "MagicPenetration"), item(RABADON, "SpellDamage")));

        // when
        List<CandidateFeatures> extracted = pipeline.extract(union, query(knownSituation()));

        // then
        assertThat(valueOf(extracted, VOID_STAFF, FeatureName.CANDIDATE_MAGIC_PEN_X_ENEMY_SPELL_BLOCK_SHARE))
                .isEqualTo(0.4);
        assertThat(valueOf(extracted, RABADON, FeatureName.CANDIDATE_MAGIC_PEN_X_ENEMY_SPELL_BLOCK_SHARE))
                .isZero();
        assertThat(valueOf(extracted, VOID_STAFF, FeatureName.ENEMY_SPELL_BLOCK_GOLD_SHARE))
                .isEqualTo(valueOf(extracted, RABADON, FeatureName.ENEMY_SPELL_BLOCK_GOLD_SHARE));
    }

    @Test
    @DisplayName("상황을 알면 후보 아이템을 요청당 한 번만 읽는다 — 후보 수만큼 조회하지 않는다")
    void extract_WhenSituationKnown_ReadsCandidateItemsOnce() {
        // given
        givenEnemySpellBlockShare(0.4);
        when(itemRepository.findAllById(any())).thenReturn(List.of());

        // when
        pipeline.extract(union, query(knownSituation()));

        // then
        verify(itemRepository, times(1)).findAllById(Set.of(VOID_STAFF, RABADON));
    }

    @Test
    @DisplayName("상황을 모르면 아이템을 읽지 않고 상황 맞춤 칸을 비워 둔다 — v3·v4 요청은 조회가 늘지 않는다")
    void extract_WhenSituationUnknown_SkipsItemLookupAndLeavesCellsEmpty() {
        // when
        List<CandidateFeatures> extracted = pipeline.extract(union, query(GameSituation.unknown()));

        // then
        verify(itemRepository, never()).findAllById(any());
        for (CandidateFeatures features : extracted) {
            for (SituationMatch match : SituationMatch.values()) {
                assertThat(features.vector().get(match.feature())).isNaN();
            }
        }
    }

    private static GameSituation knownSituation() {
        return GameSituation.of(new MyState(9, 2, 1, 3), List.of(), List.of());
    }
}
