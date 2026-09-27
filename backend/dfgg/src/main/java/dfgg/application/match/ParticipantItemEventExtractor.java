package dfgg.application.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.ParticipantItemEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** 저장된 Raw Timeline을 읽는 전용 추출기. 기존 Core 정규화 및 외부 API를 호출하지 않는다. */
@Component
public class ParticipantItemEventExtractor {

    public static final String NORMALIZATION_VERSION = "item-events-v1";
    private static final String PURCHASED = "ITEM_PURCHASED";

    private final ObjectMapper objectMapper;

    public ParticipantItemEventExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 참가자 ID, eventOrder 순으로 반환한다. 시간 누락이 있는 참가자는 전체 원본 순서를
     * 보존한다. 이 경우 시간순 복원을 보장하지 않으며 누락된 시각을 추정하지 않는다.
     */
    public List<ParticipantItemEvent> extract(String matchId, String rawTimeline) {
        if (matchId == null || matchId.isBlank() || matchId.length() > 32) {
            throw new IllegalArgumentException("경기 ID(matchId)는 비어 있지 않은 32자 이하의 문자열이어야 합니다.");
        }
        JsonNode root = readTimeline(rawTimeline);
        JsonNode rawMatchId = root.path("metadata").path("matchId");
        if (!rawMatchId.isMissingNode() && !rawMatchId.isNull()
                && (!rawMatchId.isTextual() || !matchId.equals(rawMatchId.textValue()))) {
            throw new IllegalArgumentException("타임라인의 경기 ID(metadata.matchId)가 문자열이 아니거나 입력 경기 ID와 다릅니다.");
        }
        JsonNode frames = root.path("info").path("frames");
        if (!frames.isArray()) {
            throw new IllegalArgumentException("타임라인의 프레임 목록(info.frames)이 없거나 배열이 아닙니다.");
        }

        Map<Integer, List<IndexedItemEvent>> eventsByParticipant = new TreeMap<>();
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            JsonNode events = frames.get(frameIndex).path("events");
            if (!events.isArray()) {
                throw new IllegalArgumentException("타임라인의 이벤트 목록(events)이 없거나 배열이 아닙니다. 프레임 인덱스: " + frameIndex);
            }
            for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {
                JsonNode payload = events.get(eventIndex);
                JsonNode type = payload.path("type");
                if (!payload.isObject()) {
                    throw invalidEvent(frameIndex, eventIndex, "이벤트는 JSON 객체여야 합니다.");
                }
                if (!type.isTextual() || type.textValue().isBlank()) {
                    throw invalidEvent(frameIndex, eventIndex, "이벤트 유형(type)은 비어 있지 않은 문자열이어야 합니다.");
                }
                if (!type.textValue().startsWith("ITEM_")) {
                    continue;
                }
                IndexedItemEvent event = readItemEvent(payload, frameIndex, eventIndex);
                eventsByParticipant.computeIfAbsent(event.participantId(), ignored -> new ArrayList<>())
                        .add(event);
            }
        }

        List<ParticipantItemEvent> result = new ArrayList<>();
        for (List<IndexedItemEvent> events : eventsByParticipant.values()) {
            if (events.stream().allMatch(event -> event.gameTimeMs() != null)) {
                events.sort(Comparator.comparing(IndexedItemEvent::gameTimeMs)
                        .thenComparingInt(IndexedItemEvent::frameIndex)
                        .thenComparingInt(IndexedItemEvent::eventIndex));
            }
            int eventOrder = 0;
            int purchaseOrder = 0;
            for (IndexedItemEvent event : events) {
                Integer currentPurchaseOrder = PURCHASED.equals(event.eventType()) ? ++purchaseOrder : null;
                result.add(new ParticipantItemEvent(
                        matchId, event.participantId(), ++eventOrder, currentPurchaseOrder,
                        event.frameIndex(), event.eventIndex(), event.eventType(), event.itemId(),
                        event.beforeItemId(), event.afterItemId(), event.gameTimeMs(),
                        event.sourcePayload(), NORMALIZATION_VERSION));
            }
        }
        return List.copyOf(result);
    }

    private JsonNode readTimeline(String rawTimeline) {
        if (rawTimeline == null || rawTimeline.isBlank()) {
            throw new IllegalArgumentException("타임라인 원본 JSON이 비어 있습니다.");
        }
        try {
            JsonNode root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(rawTimeline);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("타임라인 원본은 JSON 객체여야 합니다.");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("타임라인 원본 JSON을 해석할 수 없습니다. JSON 형식을 확인해 주세요.", exception);
        }
    }

    private IndexedItemEvent readItemEvent(JsonNode payload, int frameIndex, int eventIndex) {
        String eventType = payload.get("type").textValue();
        Integer participantId = optionalInteger(payload, "participantId", frameIndex, eventIndex);
        Integer itemId = optionalInteger(payload, "itemId", frameIndex, eventIndex);
        if (eventType.length() > 32) {
            throw invalidEvent(frameIndex, eventIndex, "이벤트 유형(type)은 32자 이하여야 합니다.");
        }
        if (participantId == null || participantId <= 0) {
            throw invalidEvent(frameIndex, eventIndex, "참가자 ID(participantId)가 없거나 0 이하입니다.");
        }
        if (PURCHASED.equals(eventType) && (itemId == null || itemId <= 0)) {
            throw invalidEvent(frameIndex, eventIndex, "구매 이벤트(ITEM_PURCHASED)의 아이템 ID(itemId)가 없거나 0 이하입니다.");
        }
        JsonNode timestamp = payload.path("timestamp");
        Long gameTimeMs = null;
        if (!timestamp.isMissingNode() && !timestamp.isNull()) {
            if (!timestamp.isIntegralNumber() || !timestamp.canConvertToLong() || timestamp.longValue() < 0) {
                throw invalidEvent(frameIndex, eventIndex, "이벤트 시각(timestamp)은 0 이상의 64비트 정수여야 합니다.");
            }
            gameTimeMs = timestamp.longValue();
        }
        return new IndexedItemEvent(participantId, frameIndex, eventIndex, eventType, itemId,
                optionalInteger(payload, "beforeId", frameIndex, eventIndex),
                optionalInteger(payload, "afterId", frameIndex, eventIndex),
                gameTimeMs, payload.toString());
    }

    private Integer optionalInteger(JsonNode payload, String field, int frameIndex, int eventIndex) {
        JsonNode value = payload.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalidEvent(frameIndex, eventIndex, "필드 '" + field + "'는 32비트 정수여야 합니다.");
        }
        return value.intValue();
    }

    private IllegalArgumentException invalidEvent(int frameIndex, int eventIndex, String reason) {
        // 원본 JSON/PUUID는 오류 메시지에 포함하지 않는다.
        return new IllegalArgumentException("타임라인 이벤트를 추출할 수 없습니다. 프레임 인덱스 " + frameIndex
                + ", 이벤트 인덱스 " + eventIndex + ": " + reason);
    }

    private record IndexedItemEvent(
            int participantId, int frameIndex, int eventIndex, String eventType,
            Integer itemId, Integer beforeItemId, Integer afterItemId,
            Long gameTimeMs, String sourcePayload
    ) {
    }
}
