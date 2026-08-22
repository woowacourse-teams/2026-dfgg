package dfgg.application.mining;

import dfgg.application.embedding.ChampionItemEmbeddingTrainer;
import dfgg.domain.embedding.TrainingConfig;
import dfgg.domain.embedding.Window;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbeddingTrainingBatchService {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final ItemRepository itemRepository;
    private final MatchParticipantWindowBuilder windowBuilder;
    private final ChampionItemEmbeddingTrainer trainer;

    public EmbeddingTrainingBatchService(
            NormalizedMatchParticipantRepository participantRepository,
            ItemRepository itemRepository,
            MatchParticipantWindowBuilder windowBuilder,
            ChampionItemEmbeddingTrainer trainer
    ) {
        this.participantRepository = participantRepository;
        this.itemRepository = itemRepository;
        this.windowBuilder = windowBuilder;
        this.trainer = trainer;
    }

    @Transactional(readOnly = true)
    public Map<String, double[]> trainFromMatchData(double winWeight, TrainingConfig config) {
        Map<String, List<NormalizedMatchParticipant>> byMatch = participantRepository.findAll().stream()
                .collect(Collectors.groupingBy(NormalizedMatchParticipant::getMatchId));

        List<Window> windows = new ArrayList<>();
        for (List<NormalizedMatchParticipant> matchParticipants : byMatch.values()) {
            windows.addAll(windowBuilder.buildMatchWindows(matchParticipants, winWeight));
        }
        windows.addAll(windowBuilder.buildContentContextWindows(itemRepository.findAll()));

        return trainer.train(windows, config);
    }
}
