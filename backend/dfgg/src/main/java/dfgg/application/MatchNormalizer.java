package dfgg.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.infrastructure.external.dto.MatchParticipant;
import dfgg.infrastructure.external.dto.MatchResponse;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class MatchNormalizer {

    private final ObjectMapper objectMapper;
    private final CoreItemPurchaseOrderCalculator purchaseOrderCalculator;

    public MatchNormalizer(
            ObjectMapper objectMapper,
            CoreItemPurchaseOrderCalculator purchaseOrderCalculator
    ) {
        this.objectMapper = objectMapper;
        this.purchaseOrderCalculator = purchaseOrderCalculator;
    }

    public NormalizedMatch normalize(
            String matchId,
            String rawMatchData,
            String rawTimelineData,
            Collection<Integer> coreItemIds
    ) {
        Objects.requireNonNull(coreItemIds, "coreItemIds must not be null");
        Set<Integer> coreItems = Set.copyOf(coreItemIds);
        MatchResponse match = read(rawMatchData, MatchResponse.class, "match");
        MatchTimelineResponse timeline = read(rawTimelineData, MatchTimelineResponse.class, "timeline");
        if (match.info() == null || match.info().participants() == null || match.info().participants().isEmpty()) {
            throw new IllegalArgumentException("match participants must not be empty");
        }

        List<NormalizedParticipant> participants = Stream.iterate(0, index -> index + 1)
                .limit(match.info().participants().size())
                .map(index -> normalizeParticipant(
                        match.info().participants().get(index),
                        index + 1,
                        timeline,
                        coreItems
                ))
                .toList();

        return new NormalizedMatch(
                matchId,
                normalizePatch(match.info().gameVersion()),
                Objects.requireNonNull(match.info().queueId(), "queueId must not be null"),
                participants
        );
    }

    private NormalizedParticipant normalizeParticipant(
            MatchParticipant participant,
            int fallbackParticipantId,
            MatchTimelineResponse timeline,
            Set<Integer> coreItemIds
    ) {
        Objects.requireNonNull(participant, "participant must not be null");
        int participantId = participant.participantId() != null
                ? participant.participantId()
                : timeline.participantIdForPuuid(participant.puuid())
                        .orElse(fallbackParticipantId);
        List<Integer> finalCoreItems = itemIds(participant)
                .filter(coreItemIds::contains)
                .distinct()
                .toList();
        var purchaseOrder = purchaseOrderCalculator.calculate(
                timeline,
                participantId,
                finalCoreItems,
                coreItemIds
        );

        return new NormalizedParticipant(
                participant.puuid(),
                participantId,
                participant.championId(),
                participant.teamId(),
                participant.teamPosition(),
                participant.win(),
                finalCoreItems,
                purchaseOrder.orElse(List.of()),
                purchaseOrder.isPresent()
        );
    }

    private Stream<Integer> itemIds(MatchParticipant participant) {
        return Stream.of(
                participant.item0(),
                participant.item1(),
                participant.item2(),
                participant.item3(),
                participant.item4(),
                participant.item5()
        ).filter(Predicate.not(Objects::isNull)).filter(itemId -> itemId > 0);
    }

    private String normalizePatch(String gameVersion) {
        if (gameVersion == null || gameVersion.isBlank()) {
            throw new IllegalArgumentException("gameVersion must not be blank");
        }
        String[] components = gameVersion.split("\\.");
        if (components.length < 2) {
            throw new IllegalArgumentException("gameVersion must contain major and minor versions");
        }
        return components[0] + "." + components[1];
    }

    private <T> T read(String rawData, Class<T> type, String dataName) {
        if (rawData == null || rawData.isBlank()) {
            throw new IllegalArgumentException(dataName + " raw data must not be blank");
        }
        try {
            return objectMapper.readValue(rawData, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(dataName + " raw data is invalid", exception);
        }
    }
}
