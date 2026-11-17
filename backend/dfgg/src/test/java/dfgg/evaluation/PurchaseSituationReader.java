package dfgg.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.CoreItemPurchaseOrderCalculator;
import dfgg.domain.match.CoreItemAcquisition;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 저장된 타임라인에서 참가자가 k번째 Core를 산 순간의 상황({@link PurchaseSituation})을 읽는다.
 * 구매 시각은 정규화와 같은 {@link CoreItemPurchaseOrderCalculator}로 구하고, 골드는 그 시각이 속한 1분 프레임 값을 쓴다.
 */
public final class PurchaseSituationReader {

    private static final long DEFAULT_FRAME_INTERVAL_MS = 60_000L;
    private static final double MILLIS_PER_MINUTE = 60_000.0;

    private final ObjectMapper objectMapper;
    private final CoreItemPurchaseOrderCalculator purchaseOrderCalculator;
    private final Set<Integer> coreItemIds;

    public PurchaseSituationReader(
            ObjectMapper objectMapper,
            CoreItemPurchaseOrderCalculator purchaseOrderCalculator,
            Collection<Integer> coreItemIds
    ) {
        this.objectMapper = objectMapper;
        this.purchaseOrderCalculator = purchaseOrderCalculator;
        this.coreItemIds = Set.copyOf(coreItemIds);
    }

    /**
     * {@code me}의 저장된 Core 구매 순서와 같은 길이로 돌려준다. 다시 계산한 순서가 저장된 순서와 다르면 모두 모름으로 둔다.
     */
    public List<PurchaseSituation> read(
            String rawTimeline, NormalizedMatchParticipant me, List<NormalizedMatchParticipant> matchParticipants
    ) {
        List<Integer> purchaseOrder = me.getCoreItemPurchaseOrder();
        Optional<JsonNode> root = parse(rawTimeline);
        if (root.isEmpty()) {
            return unknownFor(purchaseOrder);
        }
        Optional<List<CoreItemAcquisition>> acquisitions = acquisitionsOf(root.get(), me);
        if (acquisitions.isEmpty() || !itemIdsOf(acquisitions.get()).equals(purchaseOrder)) {
            return unknownFor(purchaseOrder);
        }
        Map<Integer, Integer> teamByParticipant = teamByParticipant(matchParticipants);
        JsonNode info = root.get().path("info");
        List<PurchaseSituation> situations = new ArrayList<>();
        for (CoreItemAcquisition acquisition : acquisitions.get()) {
            JsonNode participantFrames = frameAt(info, acquisition.gameTimeMs()).path("participantFrames");
            situations.add(new PurchaseSituation(
                    acquisition.gameTimeMs() / MILLIS_PER_MINUTE,
                    teamGoldDiff(participantFrames, teamByParticipant, me.getTeamId()),
                    totalGold(participantFrames.path(String.valueOf(me.getParticipantId())))
            ));
        }
        return situations;
    }

    private Optional<JsonNode> parse(String rawTimeline) {
        if (rawTimeline == null || rawTimeline.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readTree(rawTimeline));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private Optional<List<CoreItemAcquisition>> acquisitionsOf(JsonNode root, NormalizedMatchParticipant me) {
        try {
            MatchTimelineResponse timeline = objectMapper.treeToValue(root, MatchTimelineResponse.class);
            return purchaseOrderCalculator.calculateWithAcquiredTimes(
                    timeline, me.getParticipantId(), me.getFinalCoreItemIds(), coreItemIds);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private List<Integer> itemIdsOf(List<CoreItemAcquisition> acquisitions) {
        return acquisitions.stream().map(CoreItemAcquisition::itemId).toList();
    }

    private Map<Integer, Integer> teamByParticipant(List<NormalizedMatchParticipant> matchParticipants) {
        Map<Integer, Integer> teams = new HashMap<>();
        for (NormalizedMatchParticipant participant : matchParticipants) {
            teams.put(participant.getParticipantId(), participant.getTeamId());
        }
        return teams;
    }

    /** 구매 시각이 속한 프레임(시각 ÷ 프레임 간격). 마지막 프레임을 넘으면 마지막 프레임이다. */
    private JsonNode frameAt(JsonNode info, long gameTimeMs) {
        JsonNode frames = info.path("frames");
        if (frames.isEmpty()) {
            return frames;
        }
        long index = gameTimeMs / frameIntervalOf(info);
        return frames.get((int) Math.min(index, frames.size() - 1));
    }

    private long frameIntervalOf(JsonNode info) {
        long interval = info.path("frameInterval").asLong(DEFAULT_FRAME_INTERVAL_MS);
        if (interval <= 0) {
            return DEFAULT_FRAME_INTERVAL_MS;
        }
        return interval;
    }

    /** 프레임의 참가자 중 하나라도 골드나 팀을 모르면 차이를 내지 않는다. 일부만 더하면 차이가 조용히 틀린다. */
    private Integer teamGoldDiff(JsonNode participantFrames, Map<Integer, Integer> teamByParticipant, Integer myTeam) {
        if (participantFrames.isEmpty()) {
            return null;
        }
        int diff = 0;
        Iterator<Map.Entry<String, JsonNode>> entries = participantFrames.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            Integer gold = totalGold(entry.getValue());
            Integer team = teamOf(entry.getKey(), teamByParticipant);
            if (gold == null || team == null) {
                return null;
            }
            diff += signedGold(gold, Objects.equals(team, myTeam));
        }
        return diff;
    }

    private Integer teamOf(String participantKey, Map<Integer, Integer> teamByParticipant) {
        try {
            return teamByParticipant.get(Integer.valueOf(participantKey));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int signedGold(int gold, boolean sameTeam) {
        if (sameTeam) {
            return gold;
        }
        return -gold;
    }

    private Integer totalGold(JsonNode participantFrame) {
        JsonNode gold = participantFrame.path("totalGold");
        if (!gold.isIntegralNumber()) {
            return null;
        }
        return gold.intValue();
    }

    private List<PurchaseSituation> unknownFor(List<Integer> purchaseOrder) {
        return Collections.nCopies(purchaseOrder.size(), PurchaseSituation.unknown());
    }
}
