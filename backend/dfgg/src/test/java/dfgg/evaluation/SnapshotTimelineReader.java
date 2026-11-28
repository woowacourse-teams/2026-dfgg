package dfgg.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.CoreItemPurchaseOrderCalculator;
import dfgg.application.match.MatchStateTimelineReader;
import dfgg.domain.match.CoreItemAcquisition;
import dfgg.domain.match.MatchStateTimeline;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 경기 한 판의 타임라인 원본에서, 스냅샷에 상황을 채우는 데 필요한 두 가지를 읽는다 —
 * 10명의 상태 기록과, 참가자가 Core를 얻은 시각.
 *
 * <p>원본은 경기당 한 번만 읽어 넘겨받는다. 읽을 수 없는 경기는 예외를 던지지 않고 빈 결과로 답한다.
 * 그 경기의 스냅샷은 상황이 "모름"으로 나가고, 내보내기는 계속된다.
 */
public final class SnapshotTimelineReader {

    private final MatchStateTimelineReader stateReader;
    private final CoreItemPurchaseOrderCalculator purchaseOrderCalculator;
    private final ObjectMapper objectMapper;
    private final Set<Integer> coreItemIds;

    public SnapshotTimelineReader(
            MatchStateTimelineReader stateReader, CoreItemPurchaseOrderCalculator purchaseOrderCalculator,
            ObjectMapper objectMapper, Collection<Integer> coreItemIds
    ) {
        this.stateReader = stateReader;
        this.purchaseOrderCalculator = purchaseOrderCalculator;
        this.objectMapper = objectMapper;
        this.coreItemIds = Set.copyOf(coreItemIds);
    }

    /** @param rawTimeline 타임라인 원본. 저장돼 있지 않으면 null */
    public SnapshotTimelines read(String rawTimeline) {
        if (rawTimeline == null) {
            return new SnapshotTimelines(false, Optional.empty(), Optional.empty());
        }
        return new SnapshotTimelines(true, stateReader.read(rawTimeline, coreItemIds), parse(rawTimeline));
    }

    private Optional<MatchTimelineResponse> parse(String rawTimeline) {
        try {
            return Optional.ofNullable(objectMapper.readValue(rawTimeline, MatchTimelineResponse.class));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    /** 한 경기에서 읽은 것. 참가자마다 구매 시각을 물을 수 있다. */
    public final class SnapshotTimelines {

        private final boolean found;
        private final Optional<MatchStateTimeline> states;
        private final Optional<MatchTimelineResponse> purchases;

        private SnapshotTimelines(
                boolean found, Optional<MatchStateTimeline> states, Optional<MatchTimelineResponse> purchases
        ) {
            this.found = found;
            this.states = states;
            this.purchases = purchases;
        }

        /** 타임라인 원본이 저장돼 있었는가. */
        public boolean found() {
            return found;
        }

        /** 10명의 상태 기록. 시각이나 참가자 번호가 빠진 기록이 있으면 비어 있다. */
        public Optional<MatchStateTimeline> states() {
            return states;
        }

        /** 이 참가자가 Core를 얻은 시각을 구매 순서대로. 하나라도 시각을 알 수 없으면 비어 있다. */
        public Optional<List<CoreItemAcquisition>> acquisitionsOf(NormalizedMatchParticipant participant) {
            return purchases.flatMap(timeline -> purchaseOrderCalculator.calculateWithAcquiredTimes(
                    timeline, participant.getParticipantId(), participant.getFinalCoreItemIds(), coreItemIds));
        }

        /**
         * 상황을 채운 스냅샷 중, 그 시각에 이 참가자가 실제로 들고 있던 Core가 스냅샷의 구매 이력과 다른 것의 수.
         *
         * <p>스냅샷의 구매 이력은 "끝까지 남은 Core의 구매 순서"의 앞부분이라, 샀다가 나중에 판 Core는 들어 있지 않다.
         * 아군과 상대의 Core는 그 시각의 실제 보유에서 읽는다. 그래서 이 차이가 있는 스냅샷에서는 내 Core 가격이
         * 다른 선수보다 적게 잡힌다. 학습 데이터를 바꾸지 않고 그 크기만 잰다.
         *
         * @param acquisitions {@link #acquisitionsOf}가 이 참가자에게 돌려준 것
         */
        public long countHeldCoreMismatches(
                NormalizedMatchParticipant participant, List<CoreItemAcquisition> acquisitions,
                List<SnapshotQuery> snapshots
        ) {
            if (states.isEmpty()) {
                return 0;
            }
            return snapshots.stream()
                    .filter(snapshot -> !snapshot.query().situation().isUnknown())
                    .filter(snapshot -> !heldCoresAt(acquisitions.get(snapshot.purchaseStep()).gameTimeMs(), participant)
                            .equals(sorted(snapshot.query().purchasedItemIds())))
                    .count();
        }

        private List<Long> heldCoresAt(long gameTimeMs, NormalizedMatchParticipant participant) {
            return sorted(states.get().before(gameTimeMs).of(participant.getParticipantId()).coreItemIds().stream()
                    .map(Long::valueOf)
                    .toList());
        }

        private List<Long> sorted(List<Long> itemIds) {
            return itemIds.stream().sorted().toList();
        }
    }
}
