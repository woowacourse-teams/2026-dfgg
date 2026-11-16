package dfgg.application.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.CoreItemChange;
import dfgg.domain.match.MatchStateTimeline;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 저장된 Raw Timeline에서 경기 상태 기록을 읽는다. 외부 API를 호출하지 않는다. */
@Component
public class MatchStateTimelineReader {

    private static final String CHAMPION_KILL = "CHAMPION_KILL";
    private static final String LEVEL_UP = "LEVEL_UP";
    private static final TypeReference<List<MatchTimelineResponse.Frame>> FRAMES = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public MatchStateTimelineReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 상태를 만들 수 없으면 빈 결과를 돌려준다. 킬·레벨업·Core 기록 하나라도 시각이나 참가자 번호가 없으면
     * 통째로 만들지 않는다.
     * 빠진 기록을 건너뛰면 실제보다 적게 센 상태가 조용히 쓰이기 때문이다.
     *
     * @param coreItemIds Core로 볼 아이템. 서빙과 같은 목록(`ItemService.findCoreItemIds()`)을 넘긴다.
     */
    public Optional<MatchStateTimeline> read(String rawTimeline, Collection<Integer> coreItemIds) {
        Optional<JsonNode> frames = framesOf(rawTimeline);
        if (frames.isEmpty()) {
            return Optional.empty();
        }
        Optional<List<MatchStateTimeline.Event>> stateEvents = stateEventsOf(frames.get());
        Optional<List<MatchStateTimeline.Event>> itemEvents =
                itemEventsOf(frames.get(), new CoreItemChangeReader(coreItemIds));
        if (stateEvents.isEmpty() || itemEvents.isEmpty()) {
            return Optional.empty();
        }
        List<MatchStateTimeline.Event> events = new ArrayList<>(stateEvents.get());
        events.addAll(itemEvents.get());
        return Optional.of(new MatchStateTimeline(events));
    }

    private Optional<JsonNode> framesOf(String rawTimeline) {
        if (rawTimeline == null || rawTimeline.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode frames = objectMapper.readTree(rawTimeline).path("info").path("frames");
            return frames.isArray() ? Optional.of(frames) : Optional.empty();
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    /**
     * 아이템 이벤트는 정규화가 쓰는 타입 그대로 읽어 Core 구매 순서 계산기와 같은 규칙({@link CoreItemChangeReader})에 넘긴다.
     * Core가 아닌 아이템 기록은 보유를 바꾸지 않으니 시각이 없어도 문제 삼지 않는다.
     */
    private Optional<List<MatchStateTimeline.Event>> itemEventsOf(JsonNode frames, CoreItemChangeReader changeReader) {
        Optional<List<MatchTimelineResponse.Event>> timelineEvents = timelineEventsOf(frames);
        if (timelineEvents.isEmpty()) {
            return Optional.empty();
        }
        List<MatchStateTimeline.Event> events = new ArrayList<>();
        for (MatchTimelineResponse.Event event : timelineEvents.get()) {
            Optional<CoreItemChange> change = changeReader.read(event).filter(read -> !read.changesNothing());
            if (change.isEmpty()) {
                continue;
            }
            if (event.timestamp() == null || event.participantId() == null) {
                return Optional.empty();
            }
            events.add(new MatchStateTimeline.CoreItemChanged(event.timestamp(), event.participantId(), change.get()));
        }
        return Optional.of(events);
    }

    /** 값의 모양이 타입과 맞지 않으면(숫자 자리에 글자 등) 읽을 수 없는 타임라인으로 본다. */
    private Optional<List<MatchTimelineResponse.Event>> timelineEventsOf(JsonNode frames) {
        try {
            List<MatchTimelineResponse.Frame> typedFrames = objectMapper.convertValue(frames, FRAMES);
            return Optional.of(typedFrames.stream()
                    .filter(Objects::nonNull)
                    .map(MatchTimelineResponse.Frame::events)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .toList());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<List<MatchStateTimeline.Event>> stateEventsOf(JsonNode frames) {
        List<MatchStateTimeline.Event> events = new ArrayList<>();
        for (JsonNode frame : frames) {
            for (JsonNode event : frame.path("events")) {
                if (!isStateEvent(event)) {
                    continue;
                }
                if (!isComplete(event)) {
                    return Optional.empty();
                }
                events.add(eventOf(event));
            }
        }
        return Optional.of(events);
    }

    private boolean isStateEvent(JsonNode event) {
        String type = event.path("type").asText();
        return CHAMPION_KILL.equals(type) || LEVEL_UP.equals(type);
    }

    /**
     * 시각과 함께 누구의 기록인지, 무슨 값인지가 모두 정수로 있어야 한다. 값이 빠지면 0으로 읽혀
     * 데스가 참가자 0에게 가거나 킬이 "처치한 참가자 없음"(killerId 0)과 섞이기 때문이다.
     * 어시스트 목록은 없을 수 있고, 있으면 모두 정수여야 한다.
     */
    private boolean isComplete(JsonNode event) {
        if (!event.path("timestamp").isIntegralNumber()) {
            return false;
        }
        if (LEVEL_UP.equals(event.path("type").asText())) {
            return event.path("participantId").isIntegralNumber() && event.path("level").isIntegralNumber();
        }
        return event.path("killerId").isIntegralNumber()
                && event.path("victimId").isIntegralNumber()
                && hasIntegralAssistants(event.path("assistingParticipantIds"));
    }

    private boolean hasIntegralAssistants(JsonNode assistants) {
        if (assistants.isMissingNode()) {
            return true;
        }
        if (!assistants.isArray()) {
            return false;
        }
        for (JsonNode assistant : assistants) {
            if (!assistant.isIntegralNumber()) {
                return false;
            }
        }
        return true;
    }

    private MatchStateTimeline.Event eventOf(JsonNode event) {
        if (CHAMPION_KILL.equals(event.path("type").asText())) {
            return killOf(event);
        }
        return levelUpOf(event);
    }

    private MatchStateTimeline.LevelUp levelUpOf(JsonNode event) {
        return new MatchStateTimeline.LevelUp(
                event.path("timestamp").longValue(),
                event.path("participantId").intValue(),
                event.path("level").intValue()
        );
    }

    private MatchStateTimeline.Kill killOf(JsonNode event) {
        List<Integer> assistingParticipantIds = new ArrayList<>();
        for (JsonNode assistant : event.path("assistingParticipantIds")) {
            assistingParticipantIds.add(assistant.intValue());
        }
        return new MatchStateTimeline.Kill(
                event.path("timestamp").longValue(),
                event.path("killerId").intValue(),
                event.path("victimId").intValue(),
                assistingParticipantIds
        );
    }
}
