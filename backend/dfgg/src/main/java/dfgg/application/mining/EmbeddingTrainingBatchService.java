package dfgg.application.mining;

import dfgg.application.embedding.ChampionItemEmbeddingTrainer;
import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import dfgg.domain.embedding.TrainingConfig;
import dfgg.domain.embedding.Window;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class EmbeddingTrainingBatchService {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final ItemRepository itemRepository;
    private final EmbeddingRepository embeddingRepository;
    private final MatchParticipantWindowBuilder windowBuilder;
    private final ChampionItemEmbeddingTrainer trainer;
    private final EntityManager entityManager;
    private final int matchPageSize;

    public EmbeddingTrainingBatchService(
            NormalizedMatchParticipantRepository participantRepository,
            ItemRepository itemRepository,
            EmbeddingRepository embeddingRepository,
            MatchParticipantWindowBuilder windowBuilder,
            ChampionItemEmbeddingTrainer trainer,
            EntityManager entityManager,
            @Value("${mining.batch.match-page-size}") int matchPageSize
    ) {
        this.participantRepository = participantRepository;
        this.itemRepository = itemRepository;
        this.embeddingRepository = embeddingRepository;
        this.windowBuilder = windowBuilder;
        this.trainer = trainer;
        this.entityManager = entityManager;
        this.matchPageSize = matchPageSize;
    }

    @Transactional
    public Map<String, double[]> trainFromMatchData(double winWeight, TrainingConfig config, String algorithmVersion) {
        Assert.hasText(algorithmVersion, "algorithmVersion must not be blank");

        List<Window> windows = new ArrayList<>();
        Set<Long> championIds = new HashSet<>();

        int page = 0;
        Slice<String> matchIdPage;
        do {
            matchIdPage = participantRepository.findDistinctMatchIds(PageRequest.of(page, matchPageSize));
            List<String> matchIds = matchIdPage.getContent();
            if (!matchIds.isEmpty()) {
                List<NormalizedMatchParticipant> participants = participantRepository.findByMatchIdIn(matchIds);
                Map<String, List<NormalizedMatchParticipant>> byMatch = participants.stream()
                        .collect(Collectors.groupingBy(NormalizedMatchParticipant::getMatchId));
                for (List<NormalizedMatchParticipant> matchParticipants : byMatch.values()) {
                    windows.addAll(windowBuilder.buildMatchWindows(matchParticipants, winWeight));
                }
                for (NormalizedMatchParticipant participant : participants) {
                    championIds.add(Long.valueOf(participant.getChampionId()));
                }
                entityManager.clear();
            }
            page++;
        } while (matchIdPage.hasNext());

        List<Item> items = itemRepository.findAll();
        windows.addAll(windowBuilder.buildContentContextWindows(items));

        Map<String, double[]> embeddings = trainer.train(windows, config);
        persist(embeddings, championIds, items, algorithmVersion);
        return embeddings;
    }

    private void persist(
            Map<String, double[]> embeddings,
            Set<Long> championIds,
            List<Item> items,
            String algorithmVersion
    ) {
        Set<Long> itemIds = items.stream()
                .map(Item::getItemId)
                .collect(Collectors.toSet());

        LocalDateTime trainedAt = LocalDateTime.now();
        List<Embedding> toSave = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : embeddings.entrySet()) {
            EmbeddingEntityType entityType = classify(entry.getKey(), championIds, itemIds);
            if (entityType == null) {
                continue;
            }
            toSave.add(new Embedding(
                    entityType,
                    Long.valueOf(entry.getKey()),
                    algorithmVersion,
                    Arrays.stream(entry.getValue()).boxed().toList(),
                    trainedAt
            ));
        }

        embeddingRepository.deleteByAlgorithmVersion(algorithmVersion);
        embeddingRepository.flush();
        embeddingRepository.saveAll(toSave);
    }

    private EmbeddingEntityType classify(String token, Set<Long> championIds, Set<Long> itemIds) {
        Long entityId;
        try {
            entityId = Long.valueOf(token);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (championIds.contains(entityId)) {
            return EmbeddingEntityType.CHAMPION;
        }
        if (itemIds.contains(entityId)) {
            return EmbeddingEntityType.ITEM;
        }
        return null;
    }
}
