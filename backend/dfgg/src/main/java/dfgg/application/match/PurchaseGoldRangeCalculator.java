package dfgg.application.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.GoldRange;
import dfgg.domain.match.GoldRangeRefinement;
import dfgg.domain.match.PassiveGoldAssumption;
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
 * Raw Timeline의 프레임 골드와 전체 아이템 거래 순서로 구매 직전 Gold Range를 계산한다.
 * currentGold는 프레임 시점의 보유 골드이고, totalGold는 그때까지의 누적 획득 골드다.
 * 둘 다 구매 순간의 스냅샷은 아니므로 구매 전후 수입 시점과 실제 거래 금액은 범위로 남긴다.
 */
@Component
public class PurchaseGoldRangeCalculator {

    private final ObjectMapper objectMapper;

    public PurchaseGoldRangeCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 같은 Raw Timeline에서 추출한 이벤트와 해당 매치의 Data Dragon 카탈로그를 입력받는다.
     * 기본 Range는 앞뒤 currentGold와 거래액 구간만 사용한다. totalGold 보정과 수입 시점
     * 가정을 섞지 않아, 이후 어떤 정보가 범위를 좁혔는지 따로 확인할 수 있다.
     * 골드 스냅샷·거래 가격이 빠졌거나 비음수 수입으로 설명할 수 없는 프레임은 제외한다.
     */
    public Map<ParticipantItemEvent, GoldRange> calculate(String matchId, String rawTimeline,
                                                           List<ParticipantItemEvent> events, ItemResponse catalog) {
        Map<ParticipantItemEvent, FrameRangeContext> contexts = calculateContexts(
                matchId, rawTimeline, events, catalog);
        Map<ParticipantItemEvent, GoldRange> ranges = new HashMap<>();
        contexts.forEach((event, context) -> ranges.put(event, context.base()));
        return Map.copyOf(ranges);
    }

    /**
     * totalGold의 프레임 간 증가량으로 기본 Range를 좁힌 결과를 별도로 반환한다.
     * 이 증가량과 currentGold 변화·아이템 거래액이 양립하지 않으면 보정값은 비워 두고
     * 기본 Range를 유지한다. totalGold가 있어도 구매 전후 획득 시점은 여전히 모른다.
     */
    public Map<ParticipantItemEvent, GoldRangeRefinement> refineWithTotalGold(
            String matchId, String rawTimeline, List<ParticipantItemEvent> events, ItemResponse catalog) {
        Map<ParticipantItemEvent, FrameRangeContext> contexts = calculateContexts(
                matchId, rawTimeline, events, catalog);
        Map<ParticipantItemEvent, GoldRangeRefinement> refinements = new HashMap<>();
        contexts.forEach((event, context) -> refinements.put(event,
                new GoldRangeRefinement(context.base(), context.totalGoldRefinement(),
                        Optional.empty(), Optional.empty())));
        return Map.copyOf(refinements);
    }

    /**
     * 자연 골드와 본인 킬 지급액의 최소 수입을 구매 전후 시각에 배치한다.
     * totalGold 보정이 성립하면 그 범위 안에서 더 좁히고, 성립하지 않으면 기본 Range에서 시작한다.
     * 각 단계의 결과를 분리해 보존하므로 뒤의 가정이 실패해도 앞 단계의 범위는 남는다.
     */
    public Map<ParticipantItemEvent, GoldRangeRefinement> refineWithIncomeAssumptions(
            String matchId, String rawTimeline, List<ParticipantItemEvent> events,
            ItemResponse catalog, PassiveGoldAssumption passiveGold) {
        if (passiveGold == null) {
            throw new IllegalArgumentException("자연 골드 보정 규칙을 명시해야 합니다.");
        }
        Map<ParticipantItemEvent, FrameRangeContext> contexts = calculateContexts(
                matchId, rawTimeline, events, catalog);
        Map<ParticipantItemEvent, GoldRangeRefinement> refinements = new HashMap<>();
        contexts.forEach((event, context) -> refinements.put(
                event, refine(event, context, passiveGold)));
        return Map.copyOf(refinements);
    }

    private Map<ParticipantItemEvent, FrameRangeContext> calculateContexts(
            String matchId, String rawTimeline, List<ParticipantItemEvent> events, ItemResponse catalog) {
        validateInputs(matchId, events, catalog);
        JsonNode frames = readFrames(matchId, rawTimeline);
        Map<FrameParticipant, List<ParticipantItemEvent>> grouped = groupByFrameParticipant(matchId, events, frames);
        Map<ParticipantItemEvent, FrameRangeContext> contexts = new HashMap<>();
        for (Map.Entry<FrameParticipant, List<ParticipantItemEvent>> entry : grouped.entrySet()) {
            FrameParticipant key = entry.getKey();
            if (key.frameIndex() == 0) {
                continue;
            }
            JsonNode previousFrame = frames.get(key.frameIndex() - 1);
            JsonNode frame = frames.get(key.frameIndex());
            OptionalLong start = currentGold(previousFrame, key.participantId());
            OptionalLong end = currentGold(frame, key.participantId());
            if (start.isEmpty() || end.isEmpty()) {
                continue;
            }
            calculateFrame(entry.getValue(), previousFrame, frame, catalog.data(),
                    start.getAsLong(), end.getAsLong(), contexts);
        }
        return Map.copyOf(contexts);
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

    /** 구매 시각이 아닌 프레임 시각의 지갑 잔액이다. 양끝 중 하나가 없으면 기본 Range를 만들 수 없다. */
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

    /** 프레임 시각까지의 누적 획득 골드다. 결측은 기본 계산 실패가 아닌 선택적 보정 불가로 처리한다. */
    private OptionalLong totalGold(JsonNode frame, int participantId) {
        JsonNode gold = frame.path("participantFrames").path(Integer.toString(participantId)).path("totalGold");
        if (gold.isMissingNode() || gold.isNull()) {
            return OptionalLong.empty();
        }
        if (!gold.isIntegralNumber() || !gold.canConvertToLong() || gold.longValue() < 0) {
            throw new IllegalArgumentException("프레임 totalGold는 0 이상의 정수여야 합니다.");
        }
        return OptionalLong.of(gold.longValue());
    }

    /**
     * 프레임 안의 모든 ITEM 거래를 먼저 금액 구간으로 바꾼다. 저장 대상이 아닌 소비 아이템도
     * 지갑에는 영향을 줄 수 있으므로 거래 합계와 이벤트 순서에서 제외하지 않는다.
     */
    private void calculateFrame(List<ParticipantItemEvent> events, JsonNode previousFrame, JsonNode frame,
                                Map<String, ItemData> catalog, long start, long end,
                                Map<ParticipantItemEvent, FrameRangeContext> contexts) {
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
        // 끝 지갑 = 시작 지갑 + 구간 수입 + 모든 거래 delta.
        // 실제 거래액을 모르므로 delta 합계의 양끝으로 가능한 총수입 구간을 얻는다.
        long incomeLower = Math.max(0, walletChange - totalUpper);
        long incomeUpper = walletChange - totalLower;
        if (incomeUpper < incomeLower) {
            return;
        }
        OptionalLong earned = earnedGold(previousFrame, frame, events.getFirst().participantId());
        OptionalLong transactionTotal = compatibleTransactionTotal(earned, walletChange, totalLower, totalUpper);
        long beforeLower = 0;
        long beforeUpper = 0;
        for (int index = 0; index < events.size(); index++) {
            ParticipantItemEvent event = events.get(index);
            if ("ITEM_PURCHASED".equals(event.eventType())) {
                // 하한: 구매 전 수입을 0으로, 구매 전 거래를 각 하한으로 둔다.
                // 상한: 구매 이후 수입을 0으로, 현재 구매를 포함한 이후 거래를 각 하한으로 둔다.
                // 그래서 상한은 끝 지갑에서 구매 이후 거래 하한을 뺀 값이다.
                long lower = Math.max(0, start + beforeLower);
                long upper = end - (totalLower - beforeLower);
                if (upper >= lower) {
                    GoldRange base = new GoldRange(lower, upper);
                    Optional<GoldRange> observed = refineWithTotalGold(base, start, earned,
                            transactionTotal, beforeLower, beforeUpper,
                            totalLower - beforeLower, totalUpper - beforeUpper);
                    long beforeFloor = start + beforeLower;
                    long usableIncomeUpper = incomeUpper;
                    if (observed.isPresent()) {
                        // 전체 거래 총액이 정해졌으므로 구매 전 거래의 최소값도 강화된다.
                        // 자연 골드·킬 보정은 이 강화된 거래 바닥과 관측 구간 수입을 사용한다.
                        beforeFloor = start + Math.max(beforeLower,
                                transactionTotal.getAsLong() - (totalUpper - beforeUpper));
                        usableIncomeUpper = earned.getAsLong();
                    }
                    contexts.put(event, new FrameRangeContext(base, observed,
                            beforeFloor, usableIncomeUpper, previousFrame, frame));
                }
            }
            beforeLower += deltas.get(index).lower();
            beforeUpper += deltas.get(index).upper();
        }
    }

    /**
     * 두 totalGold 스냅샷의 차이를 프레임 구간에 획득한 골드로 읽는다.
     * 감소하거나 결측이면 모순된 값을 억지로 수입으로 쓰지 않고 보정을 건너뛴다.
     */
    private OptionalLong earnedGold(JsonNode previousFrame, JsonNode frame, int participantId) {
        OptionalLong start = totalGold(previousFrame, participantId);
        OptionalLong end = totalGold(frame, participantId);
        if (start.isEmpty() || end.isEmpty() || end.getAsLong() < start.getAsLong()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(end.getAsLong() - start.getAsLong());
    }

    /**
     * 지갑 변화 - 획득 골드 = 프레임 전체 아이템 거래의 순금액이어야 한다.
     * 이 값이 각 거래의 가능 금액을 합친 구간 밖이면 totalGold와 현재 거래 모델이
     * 일치하지 않는다. 이때 보정만 건너뛰고 currentGold 기반 기본 범위는 보존한다.
     */
    private OptionalLong compatibleTransactionTotal(OptionalLong earned, long walletChange,
                                                    long totalLower, long totalUpper) {
        if (earned.isEmpty()) {
            return OptionalLong.empty();
        }
        long transactionTotal = walletChange - earned.getAsLong();
        if (transactionTotal < totalLower || transactionTotal > totalUpper) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(transactionTotal);
    }

    /**
     * totalGold와 현재 거래 모델이 양립할 때 채택한 프레임 거래 순금액을
     * 구매 전 거래와 구매 이후 거래로 나눈다.
     * 구매 전 거래 최소값은 max(기존 최소, 전체 순금액 - 이후 최대),
     * 최대값은 min(기존 최대, 전체 순금액 - 이후 최소)다.
     * 구매 전 획득 골드는 0부터 프레임 획득량 전체까지 가능하므로 이 둘을 합쳐
     * 구매 직전 하한·상한을 얻고 기본 Range와 교집합을 취한다.
     */
    private Optional<GoldRange> refineWithTotalGold(GoldRange base, long start, OptionalLong earned,
                                                    OptionalLong transactionTotal, long beforeLower,
                                                    long beforeUpper, long afterLower, long afterUpper) {
        if (transactionTotal.isEmpty()) {
            return Optional.empty();
        }
        long feasibleBeforeLower = Math.max(beforeLower, transactionTotal.getAsLong() - afterUpper);
        long feasibleBeforeUpper = Math.min(beforeUpper, transactionTotal.getAsLong() - afterLower);
        if (feasibleBeforeUpper < feasibleBeforeLower) {
            return Optional.empty();
        }
        long lower = Math.max(base.lower(), start + feasibleBeforeLower);
        long upper = Math.min(base.upper(), start + earned.getAsLong() + feasibleBeforeUpper);
        if (upper < lower) {
            return Optional.empty();
        }
        return Optional.of(new GoldRange(lower, upper));
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

    /**
     * 카탈로그 총가격은 실제 지불액이나 판매 수익의 영수증이 아니다.
     * 구매는 [-총가격, 0], 판매는 [0, 총가격]으로 두고, 원천에 goldGain이 있는
     * UNDO만 그 값을 사용한다. DESTROYED는 지갑 변화가 없는 것으로 처리한다.
     */
    private Optional<GoldDelta> transactionDelta(ParticipantItemEvent event, JsonNode source,
                                                 Map<String, ItemData> catalog) {
        return switch (event.eventType()) {
            case "ITEM_DESTROYED" -> Optional.of(new GoldDelta(0, 0));
            case "ITEM_UNDO" -> undoDelta(source);
            case "ITEM_PURCHASED", "ITEM_SOLD" -> pricedDelta(event, catalog);
            default -> Optional.empty();
        };
    }

    /** UNDO의 goldGain은 원천 이벤트에 기록된 지갑 증감액이므로 구간 양끝에 같은 값을 둔다. */
    private Optional<GoldDelta> undoDelta(JsonNode source) {
        JsonNode gain = source.path("goldGain");
        if (!gain.isIntegralNumber() || !gain.canConvertToLong()) {
            return Optional.empty();
        }
        return Optional.of(new GoldDelta(gain.longValue(), gain.longValue()));
    }

    /** 실제 구매 지출·판매 수익이 없을 때 카탈로그 총가격으로 가능한 바깥 경계만 만든다. */
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

    /**
     * totalGold가 좁힌 범위를 우선 사용하되 자연 골드·킬의 시점 가정은 별도 결과로 둔다.
     * 특정 가정이 전체 가능한 수입을 초과하거나 범위를 비우면 그 단계만 적용하지 않는다.
     */
    private GoldRangeRefinement refine(ParticipantItemEvent purchase, FrameRangeContext context,
                                       PassiveGoldAssumption passiveGold) {
        GoldRange observedOrBase = context.totalGoldRefinement().orElse(context.base());
        Optional<IncomeSplit> passive = passiveIncome(purchase, context, passiveGold);
        if (passive.isEmpty()) {
            return new GoldRangeRefinement(context.base(), context.totalGoldRefinement(),
                    Optional.empty(), Optional.empty());
        }
        IncomeSplit passiveMinimum = passive.orElseThrow();
        Optional<GoldRange> passiveRange = observedOrBase.refine(context.beforeTransactionFloor(),
                context.incomeUpper(), passiveMinimum.before(), passiveMinimum.after());
        if (passiveRange.isEmpty()) {
            return new GoldRangeRefinement(context.base(), context.totalGoldRefinement(),
                    Optional.empty(), Optional.empty());
        }
        Optional<IncomeSplit> kills = killIncome(purchase, context);
        if (kills.isEmpty()) {
            return new GoldRangeRefinement(context.base(), context.totalGoldRefinement(),
                    passiveRange, Optional.empty());
        }
        IncomeSplit killMinimum = kills.orElseThrow();
        Optional<GoldRange> passiveAndKill = observedOrBase.refine(context.beforeTransactionFloor(),
                context.incomeUpper(), passiveMinimum.before() + killMinimum.before(),
                passiveMinimum.after() + killMinimum.after());
        return new GoldRangeRefinement(context.base(), context.totalGoldRefinement(),
                passiveRange, passiveAndKill);
    }

    /** 프레임 시작·구매·프레임 종료 시각이 모두 있으면 자연 골드 최소 수입을 양쪽에 배치한다. */
    private Optional<IncomeSplit> passiveIncome(ParticipantItemEvent purchase, FrameRangeContext context,
                                                PassiveGoldAssumption assumption) {
        OptionalLong start = frameTime(context.previousFrame());
        OptionalLong end = frameTime(context.frame());
        Long purchaseTime = purchase.gameTimeMs();
        if (start.isEmpty() || end.isEmpty() || purchaseTime == null
                || purchaseTime < start.getAsLong() || purchaseTime > end.getAsLong()) {
            return Optional.empty();
        }
        return Optional.of(new IncomeSplit(
                assumption.minimumBetween(start.getAsLong(), purchaseTime),
                assumption.minimumBetween(purchaseTime, end.getAsLong())));
    }

    private OptionalLong frameTime(JsonNode frame) {
        JsonNode timestamp = frame.path("timestamp");
        if (!timestamp.isIntegralNumber() || !timestamp.canConvertToLong() || timestamp.longValue() < 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(timestamp.longValue());
    }

    /** 본인 킬만 사용하며, 구매와 같은 timestamp의 킬은 전후 어느 쪽에도 배치하지 않는다. */
    private Optional<IncomeSplit> killIncome(ParticipantItemEvent purchase, FrameRangeContext context) {
        JsonNode events = context.frame().path("events");
        if (!events.isArray()) {
            return Optional.empty();
        }
        long frameStart = frameTime(context.previousFrame()).orElseThrow();
        long frameEnd = frameTime(context.frame()).orElseThrow();
        long before = 0;
        long after = 0;
        for (JsonNode event : events) {
            if (!"CHAMPION_KILL".equals(event.path("type").asText())
                    || !event.path("killerId").canConvertToInt()
                    || event.path("killerId").intValue() != purchase.participantId()) {
                continue;
            }
            OptionalLong time = nonnegativeLong(event.path("timestamp"));
            if (time.isEmpty() || time.getAsLong() <= frameStart || time.getAsLong() > frameEnd) {
                return Optional.empty();
            }
            if (time.getAsLong() == purchase.gameTimeMs()) {
                continue;
            }
            OptionalLong bounty = nonnegativeLong(event.path("bounty"));
            OptionalLong shutdown = nonnegativeLong(event.path("shutdownBounty"));
            if (bounty.isEmpty() || shutdown.isEmpty()) {
                return Optional.empty();
            }
            if (time.getAsLong() < purchase.gameTimeMs()) {
                before += bounty.getAsLong() + shutdown.getAsLong();
            }
            if (time.getAsLong() > purchase.gameTimeMs()) {
                after += bounty.getAsLong() + shutdown.getAsLong();
            }
        }
        return Optional.of(new IncomeSplit(before, after));
    }

    private OptionalLong nonnegativeLong(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(value.longValue());
    }

    private record FrameParticipant(int frameIndex, int participantId) {
    }

    /** 아이템 거래 한 건의 지갑 증감 구간이다. 지출은 음수, 수익은 양수로 표현한다. */
    private record GoldDelta(long lower, long upper) {
    }

    /**
     * 구매별 기본·totalGold 범위와 추가 시점 보정에 필요한 프레임 문맥이다.
     * beforeTransactionFloor는 구매 전 수입을 더하기 전의 지갑 하한이고,
     * incomeUpper는 프레임 안에서 가능한 총수입 상한이다.
     */
    private record FrameRangeContext(GoldRange base, Optional<GoldRange> totalGoldRefinement,
                                     long beforeTransactionFloor, long incomeUpper,
                                     JsonNode previousFrame, JsonNode frame) {
    }

    /** 구매 시점을 기준으로 앞뒤에 최소한 발생했다고 가정한 수입이다. */
    private record IncomeSplit(long before, long after) {
    }
}
