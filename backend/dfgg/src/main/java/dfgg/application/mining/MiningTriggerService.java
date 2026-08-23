package dfgg.application.mining;

import dfgg.domain.embedding.EmbeddingRepository;
import dfgg.domain.embedding.TrainingConfig;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import dfgg.domain.sequence.SequentialPattern;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MiningTriggerService {

    private final EmbeddingTrainingBatchService embeddingTrainingBatchService;
    private final SequentialPatternMiningBatchService sequentialPatternMiningBatchService;
    private final EmbeddingRepository embeddingRepository;
    private final MinedSequentialPatternRepository minedSequentialPatternRepository;

    public MiningTriggerService(
            EmbeddingTrainingBatchService embeddingTrainingBatchService,
            SequentialPatternMiningBatchService sequentialPatternMiningBatchService,
            EmbeddingRepository embeddingRepository,
            MinedSequentialPatternRepository minedSequentialPatternRepository
    ) {
        this.embeddingTrainingBatchService = embeddingTrainingBatchService;
        this.sequentialPatternMiningBatchService = sequentialPatternMiningBatchService;
        this.embeddingRepository = embeddingRepository;
        this.minedSequentialPatternRepository = minedSequentialPatternRepository;
    }

    public EmbeddingTrainingResult trainEmbeddings(double winWeight, TrainingConfig config, String algorithmVersion) {
        embeddingTrainingBatchService.trainFromMatchData(winWeight, config, algorithmVersion);
        long persistedCount = embeddingRepository.countByAlgorithmVersion(algorithmVersion);
        return new EmbeddingTrainingResult(persistedCount, algorithmVersion);
    }

    public SequentialPatternMiningResult mineSequentialPatterns(String queueType, int minSupport, String algorithmVersion) {
        Map<MiningScope, List<SequentialPattern>> patternsByScope =
                sequentialPatternMiningBatchService.mineFromMatchData(queueType, minSupport, algorithmVersion);
        long persistedCount = minedSequentialPatternRepository.countByAlgorithmVersion(algorithmVersion);
        return new SequentialPatternMiningResult(patternsByScope.size(), persistedCount, algorithmVersion);
    }
}
