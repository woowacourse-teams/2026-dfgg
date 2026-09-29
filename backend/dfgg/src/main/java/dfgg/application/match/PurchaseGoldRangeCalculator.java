package dfgg.application.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.GoldRange;
import dfgg.domain.match.ParticipantItemEvent;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * 프레임 양끝의 currentGold와 전체 아이템 거래액 구간으로 구매 직전 기본 Gold Range를 계산한다.
 * 구매 순간의 정확한 골드, 수입 시각, 실제 지불액을 원천에서 관측했다고 간주하지 않는다.
 */
@Component
public class PurchaseGoldRangeCalculator {

    private final ObjectMapper objectMapper;

    public PurchaseGoldRangeCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 같은 Raw Timeline에서 추출한 이벤트와 해당 매치의 Data Dragon 카탈로그를 입력받는다.
     * 골드 스냅샷·거래 가격이 빠졌거나 비음수 수입으로 설명할 수 없는 프레임의 구매는 결과에서 제외한다.
     */
    public Map<ParticipantItemEvent, GoldRange> calculate(String matchId, String rawTimeline,
                                                           List<ParticipantItemEvent> events, ItemResponse catalog) {
        validateInputs(matchId, events, catalog);
        JsonNode frames = readFrames(matchId, rawTimeline);
        Map<FrameParticipant, List<ParticipantItemEvent>> grouped = groupByFrameParticipant(matchId, events, frames);
        Map<ParticipantItemEvent, GoldRange> ranges = new HashMap<>();
        for (Map.Entry<FrameParticipant, List<ParticipantItemEvent>> entry : grouped.entrySet()) {
            FrameParticipant key = entry.getKey();
            if (key.frameIndex() == 0) {
                continue;
            }
            OptionalLong start = currentGold(frames.get(key.frameIndex() - 1), key.participantId());
            OptionalLong end = currentGold(frames.get(key.frameIndex()), key.participantId());
            if (start.isEmpty() || end.isEmpty()) {
                continue;
            }
            calculateFrame(entry.getValue(), frames.get(key.frameIndex()), catalog.data(),
                    start.getAsLong(), end.getAsLong(), ranges);
        }
        return Map.copyOf(ranges);
    }

    private void validateInputs(String matchId, List<ParticipantItemEvent> events, ItemResponse catalog) {
        if (matchId == null || matchId.isBlank() || events == null || catalog == null || catalog.data() == null) {
            throw new IllegalArgumentException("경기 ID, 전체 아이템 이벤트, 패치 카탈로그가 필요합니다.");
        }
    }

    /** 원천 프레임을 다시 읽고 입력 경기 ID와의 일치를 확인한다. */
    private JsonNode readFrames(String matchId, String rawTimeline) {
        if (rawTimeline == null || rawTimeline.isBlank()) {
            throw new IllegalArgumentException("Raw Timeline이 필요합니다.");
        }
        try {
            JsonNode root = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(rawTimeline);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Raw Timeline은 JSON 객체여야 합니다.");
            }
            JsonNode originalMatchId = root.path("metadata").path("matchId");
            if (!originalMatchId.isMissingNode() && !matchId.equals(originalMatchId.asText())) {
                throw new IllegalArgumentException("Raw Timeline의 경기 ID가 입력과 다릅니다.");
            }
            JsonNode frames = root.path("info").path("frames");
            if (!frames.isArray()) {
                throw new IllegalArgumentException("Raw Timeline의 프레임 목록이 없습니다.");
            }
            return frames;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Raw Timeline JSON을 해석할 수 없습니다.", exception);
        }
    }

    /** eventOrder가 아닌 원천 프레임·이벤트 위치로 거래를 정렬해 프레임 골드와 연결한다. */
    private Map<FrameParticipant, List<ParticipantItemEvent>> groupByFrameParticipant(
            String matchId, List<ParticipantItemEvent> events, JsonNode frames) {
        Map<FrameParticipant, List<ParticipantItemEvent>> grouped = new HashMap<>();
        for (ParticipantItemEvent event : events) {
            if (event == null || !matchId.equals(event.matchId())
                    || event.sourceFrameIndex() < 0 || event.sourceFrameIndex() >= frames.size()
                    || event.participantId() <= 0) {
                throw new IllegalArgumentException("원천 아이템 이벤트의 프레임 또는 참가자가 유효하지 않습니다.");
            }
            FrameParticipant key = new FrameParticipant(event.sourceFrameIndex(), event.participantId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
        }
        for (List<ParticipantItemEvent> frameEvents : grouped.values()) {
            frameEvents.sort(Comparator.comparingInt(ParticipantItemEvent::sourceEventIndex));
        }
        return grouped;
    }

    private OptionalLong currentGold(JsonNode frame, int participantId) {
        JsonNode gold = frame.path("participantFrames").path(Integer.toString(participantId)).path("currentGold");
        if (gold.isMissingNode() || gold.isNull()) {
            return OptionalLong.empty();
        }
        if (!gold.isIntegralNumber() || !gold.canConvertToLong() || gold.longValue() < 0) {
            throw new IllegalArgumentException("프레임 currentGold는 0 이상의 정수여야 합니다.");
        }
        return OptionalLong.of(gold.longValue());
    }

    /** 구간 전체 거래 비용을 먼저 확인한 뒤 구매별 앞·뒤 거래 경계로 Range를 만든다. */
    private void calculateFrame(List<ParticipantItemEvent> events, JsonNode frame,
                                Map<String, ItemData> catalog, long start, long end,
                                Map<ParticipantItemEvent, GoldRange> ranges) {
        List<GoldDelta> deltas = transactionDeltas(events, frame, catalog);
        if (deltas.isEmpty()) {
            return;
        }
        long totalLower = 0;
        long totalUpper = 0;
        for (GoldDelta delta : deltas) {
            totalLower += delta.lower();
            totalUpper += delta.upper();
        }
        long walletChange = end - start;
        long incomeLower = Math.max(0, walletChange - totalUpper);
        long incomeUpper = walletChange - totalLower;
        if (incomeUpper < incomeLower) {
            return;
        }
        long beforeLower = 0;
        for (int index = 0; index < events.size(); index++) {
            ParticipantItemEvent event = events.get(index);
            if ("ITEM_PURCHASED".equals(event.eventType())) {
                long lower = Math.max(0, start + beforeLower);
                long upper = end - (totalLower - beforeLower);
                if (upper >= lower) {
                    ranges.put(event, new GoldRange(lower, upper));
                }
            }
            beforeLower += deltas.get(index).lower();
        }
    }

    /** 하나라도 설명할 수 없는 거래가 있으면 프레임 전체의 Gold 흐름을 미확정으로 둔다. */
    private List<GoldDelta> transactionDeltas(List<ParticipantItemEvent> events, JsonNode frame,
                                               Map<String, ItemData> catalog) {
        List<GoldDelta> deltas = new ArrayList<>();
        int previousSourceIndex = -1;
        for (ParticipantItemEvent event : events) {
            if (event.sourceEventIndex() <= previousSourceIndex) {
                throw new IllegalArgumentException("원천 이벤트 위치가 중복되거나 순서가 뒤바뀌었습니다.");
            }
            previousSourceIndex = event.sourceEventIndex();
            JsonNode source = sourceEvent(frame, event);
            Optional<GoldDelta> delta = transactionDelta(event, source, catalog);
            if (delta.isEmpty()) {
                return List.of();
            }
            deltas.add(delta.orElseThrow());
        }
        return deltas;
    }

    /** 추출 이벤트와 Raw의 동일 프레임·이벤트를 대조해 다른 원천을 섞지 않는다. */
    private JsonNode sourceEvent(JsonNode frame, ParticipantItemEvent event) {
        JsonNode rawEvents = frame.path("events");
        if (!rawEvents.isArray() || event.sourceEventIndex() < 0
                || event.sourceEventIndex() >= rawEvents.size()) {
            throw new IllegalArgumentException("구매 Range의 원천 이벤트 위치가 Raw Timeline과 다릅니다.");
        }
        JsonNode source = rawEvents.get(event.sourceEventIndex());
        if (!source.toString().equals(event.sourcePayload())) {
            throw new IllegalArgumentException("구매 Range의 원천 이벤트가 Raw Timeline과 다릅니다.");
        }
        return source;
    }

    /** 구매·판매는 카탈로그 총가격을 상한으로, UNDO는 관측 goldGain을 사용한다. */
    private Optional<GoldDelta> transactionDelta(ParticipantItemEvent event, JsonNode source,
                                                 Map<String, ItemData> catalog) {
        return switch (event.eventType()) {
            case "ITEM_DESTROYED" -> Optional.of(new GoldDelta(0, 0));
            case "ITEM_UNDO" -> undoDelta(source);
            case "ITEM_PURCHASED", "ITEM_SOLD" -> pricedDelta(event, catalog);
            default -> Optional.empty();
        };
    }

    private Optional<GoldDelta> undoDelta(JsonNode source) {
        JsonNode gain = source.path("goldGain");
        if (!gain.isIntegralNumber() || !gain.canConvertToLong()) {
            return Optional.empty();
        }
        return Optional.of(new GoldDelta(gain.longValue(), gain.longValue()));
    }

    private Optional<GoldDelta> pricedDelta(ParticipantItemEvent event, Map<String, ItemData> catalog) {
        if (event.itemId() == null || event.itemId() <= 0) {
            return Optional.empty();
        }
        ItemData item = catalog.get(Integer.toString(event.itemId()));
        if (item == null || item.gold() == null || item.gold().total() == null || item.gold().total() < 0) {
            return Optional.empty();
        }
        long price = item.gold().total();
        if ("ITEM_PURCHASED".equals(event.eventType())) {
            return Optional.of(new GoldDelta(-price, 0));
        }
        return Optional.of(new GoldDelta(0, price));
    }

    private record FrameParticipant(int frameIndex, int participantId) {
    }

    private record GoldDelta(long lower, long upper) {
    }
}
