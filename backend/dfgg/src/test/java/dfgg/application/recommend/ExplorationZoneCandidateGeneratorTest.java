package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.utils.CosineSimilarityCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExplorationZoneCandidateGeneratorTest {

    private static final Long MY_CHAMPION_ID = 145L;

    private EmbeddingRepository embeddingRepository;
    private NormalizedMatchParticipantRepository participantRepository;
    private ExplorationZoneCandidateGenerator explorationZoneCandidateGenerator;

    @BeforeEach
    void setUp() {
        embeddingRepository = mock(EmbeddingRepository.class);
        participantRepository = mock(NormalizedMatchParticipantRepository.class);
        explorationZoneCandidateGenerator = new ExplorationZoneCandidateGenerator(
                embeddingRepository, participantRepository,
                new CosineSimilarityCalculator(), new ChampionPositionNormalizer()
        );
    }

    private List<RankedItemCandidate> rank(List<Long> enemyChampionIds) {
        return explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(
                enemyChampionIds, "counter-v1", MY_CHAMPION_ID, ChampionPosition.BOTTOM
        );
    }

    @Test
    @DisplayName("적 챔피언 여러 명 중 코사인 유사도가 가장 높은 값을 아이템의 점수로 삼아 내림차순 정렬한다")
    void rankByMaxSimilarityToEnemies_WhenMultipleEnemies_ScoresByMaxSimilarityDescending() {
        // given
        Embedding enemyOne = new Embedding(EmbeddingEntityType.CHAMPION, 101L, "counter-v1", List.of(1.0, 0.0), LocalDateTime.now());
        Embedding enemyTwo = new Embedding(EmbeddingEntityType.CHAMPION, 102L, "counter-v1", List.of(0.0, 1.0), LocalDateTime.now());
        // itemA는 enemyOne과만 완전히 같은 방향(유사도 1.0), enemyTwo와는 직교(유사도 0.0) → max = 1.0
        Embedding itemA = new Embedding(EmbeddingEntityType.ITEM, 3001L, "counter-v1", List.of(1.0, 0.0), LocalDateTime.now());
        // itemB는 두 적 모두와 어느 정도 유사(0.6, 0.8) → max = 0.8
        Embedding itemB = new Embedding(EmbeddingEntityType.ITEM, 3002L, "counter-v1", List.of(0.6, 0.8), LocalDateTime.now());

        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                eq("counter-v1"), eq(EmbeddingEntityType.CHAMPION), eq(List.of(101L, 102L))
        )).thenReturn(List.of(enemyOne, enemyTwo));
        when(embeddingRepository.findByAlgorithmVersionAndEntityType("counter-v1", EmbeddingEntityType.ITEM))
                .thenReturn(List.of(itemA, itemB));
        // 이 챔피언·포지션이 실제로 산 적 있는 아이템 = itemA, itemB 둘 다
        when(participantRepository.findDistinctPurchasedItemIds(eq(MY_CHAMPION_ID), any()))
                .thenReturn(List.of("3001", "3002"));

        // when
        List<RankedItemCandidate> ranked = rank(List.of(101L, 102L));

        // then
        assertThat(ranked).extracting(RankedItemCandidate::itemId)
                .containsExactly(3001L, 3002L);
        assertThat(ranked.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(ranked.get(1).score()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("적 챔피언 임베딩을 하나도 찾지 못하면 빈 리스트를 반환한다")
    void rankByMaxSimilarityToEnemies_WhenNoEnemyEmbeddingsFound_ReturnsEmptyList() {
        // given
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                eq("counter-v1"), eq(EmbeddingEntityType.CHAMPION), eq(List.of(999L))
        )).thenReturn(List.of());

        // when
        List<RankedItemCandidate> ranked = explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(
                List.of(999L), "counter-v1", MY_CHAMPION_ID, ChampionPosition.BOTTOM
        );

        // then
        assertThat(ranked).isEmpty();
    }

    @Test
    @DisplayName("적과의 유사도가 아무리 높아도 내 챔피언·포지션이 실제로 산 적 없는 아이템은 후보에서 제외한다")
    void rankByMaxSimilarityToEnemies_WhenItemNeverPurchasedByChampion_ExcludesItFromCandidates() {
        // given: 카운터 공간은 태그를 학습하지 않으므로(콘텐츠 문맥은 정체성 공간 전용)
        //        "엔챈터 전용 아이템"이라는 걸 이 공간만으로는 알 수 없다 — 실측 구매 이력으로 걸러야 한다.
        Embedding enemy = new Embedding(EmbeddingEntityType.CHAMPION, 101L, "counter-v1", List.of(1.0, 0.0), LocalDateTime.now());
        // moonstone은 적과 완전히 같은 방향(유사도 1.0)이라 필터가 없으면 1등이 된다
        Embedding moonstone = new Embedding(EmbeddingEntityType.ITEM, 6617L, "counter-v1", List.of(1.0, 0.0), LocalDateTime.now());
        Embedding krakenSlayer = new Embedding(EmbeddingEntityType.ITEM, 6672L, "counter-v1", List.of(0.6, 0.8), LocalDateTime.now());

        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                eq("counter-v1"), eq(EmbeddingEntityType.CHAMPION), eq(List.of(101L))
        )).thenReturn(List.of(enemy));
        when(embeddingRepository.findByAlgorithmVersionAndEntityType("counter-v1", EmbeddingEntityType.ITEM))
                .thenReturn(List.of(moonstone, krakenSlayer));
        // 카이사(챔피언 145)는 크라켄 학살자만 산 적 있고 월석 재생기는 산 적 없다
        when(participantRepository.findDistinctPurchasedItemIds(MY_CHAMPION_ID, List.of("BOTTOM")))
                .thenReturn(List.of("6672"));

        // when
        List<RankedItemCandidate> ranked = rank(List.of(101L));

        // then
        assertThat(ranked).extracting(RankedItemCandidate::itemId).containsExactly(6672L);
    }
}
