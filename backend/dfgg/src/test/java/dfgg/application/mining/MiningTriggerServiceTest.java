package dfgg.application.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.embedding.EmbeddingRepository;
import dfgg.domain.embedding.TrainingConfig;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import dfgg.domain.sequence.SequentialPattern;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MiningTriggerServiceTest {

    private EmbeddingTrainingBatchService embeddingTrainingBatchService;
    private SequentialPatternMiningBatchService sequentialPatternMiningBatchService;
    private EmbeddingRepository embeddingRepository;
    private MinedSequentialPatternRepository minedSequentialPatternRepository;
    private MiningTriggerService miningTriggerService;

    @BeforeEach
    void setUp() {
        embeddingTrainingBatchService = mock(EmbeddingTrainingBatchService.class);
        sequentialPatternMiningBatchService = mock(SequentialPatternMiningBatchService.class);
        embeddingRepository = mock(EmbeddingRepository.class);
        minedSequentialPatternRepository = mock(MinedSequentialPatternRepository.class);
        miningTriggerService = new MiningTriggerService(
                embeddingTrainingBatchService,
                sequentialPatternMiningBatchService,
                embeddingRepository,
                minedSequentialPatternRepository
        );
    }

    @Test
    @DisplayName("임베딩 학습을 실행한 뒤 저장된 개수를 결과로 반환한다")
    void trainEmbeddings_WhenTrainingCompletes_ReturnsPersistedCountFromRepository() {
        // given
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);
        when(embeddingRepository.countByAlgorithmVersion("sgns-v1")).thenReturn(11L);

        // when
        EmbeddingTrainingResult result = miningTriggerService.trainEmbeddings(3.0, config, "sgns-v1");

        // then
        verify(embeddingTrainingBatchService).trainFromMatchData(3.0, config, "sgns-v1");
        assertThat(result.persistedEmbeddingCount()).isEqualTo(11L);
        assertThat(result.algorithmVersion()).isEqualTo("sgns-v1");
    }

    @Test
    @DisplayName("순차 패턴 마이닝을 실행한 뒤 스코프 개수와 저장된 패턴 개수를 결과로 반환한다")
    void mineSequentialPatterns_WhenMiningCompletes_ReturnsScopeCountAndPersistedPatternCount() {
        // given
        MiningScope scopeA = new MiningScope(1L, "TOP", "GOLD", "14.1");
        MiningScope scopeB = new MiningScope(2L, "MID", "GOLD", "14.1");
        Map<MiningScope, List<SequentialPattern>> patternsByScope = Map.of(
                scopeA, List.of(new SequentialPattern(List.of(3071L), 10)),
                scopeB, List.of()
        );
        when(sequentialPatternMiningBatchService.mineFromMatchData(eq("RANKED_SOLO_5x5"), eq(5), any()))
                .thenReturn(patternsByScope);
        when(minedSequentialPatternRepository.countByAlgorithmVersion("prefixspan-v1")).thenReturn(7L);

        // when
        SequentialPatternMiningResult result =
                miningTriggerService.mineSequentialPatterns("RANKED_SOLO_5x5", 5, "prefixspan-v1");

        // then
        verify(sequentialPatternMiningBatchService).mineFromMatchData("RANKED_SOLO_5x5", 5, "prefixspan-v1");
        assertThat(result.scopeCount()).isEqualTo(2);
        assertThat(result.persistedPatternCount()).isEqualTo(7L);
        assertThat(result.algorithmVersion()).isEqualTo("prefixspan-v1");
    }
}
