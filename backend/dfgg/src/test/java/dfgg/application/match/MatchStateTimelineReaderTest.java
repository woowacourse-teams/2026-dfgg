package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.CoreItemAcquisition;
import dfgg.domain.match.CoreItemInventory;
import dfgg.domain.match.MatchState;
import dfgg.domain.match.MatchStateTimeline;
import dfgg.domain.match.ParticipantState;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MatchStateTimelineReaderTest {

    /** 서빙과 같은 Core 목록의 일부. 3047(판금 장화)처럼 신발도 들어 있다. 1036(롱소드)은 Core가 아니다. */
    private static final Set<Integer> CORE_ITEMS = Set.of(3071, 3053, 3047, 6610);

    private final MatchStateTimelineReader reader = new MatchStateTimelineReader(new ObjectMapper());

    @Test
    @DisplayName("기준 시각 이전의 킬을 참가자별 킬·데스·어시스트로 센다")
    void before_WhenKillsHappenedEarlier_CountsKillsDeathsAndAssists() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"CHAMPION_KILL","timestamp":1000,"killerId":4,"victimId":9,"assistingParticipantIds":[2,5]},
                {"type":"ITEM_PURCHASED","timestamp":1500,"participantId":4,"itemId":1036},
                {"type":"CHAMPION_KILL","timestamp":2000,"killerId":2,"victimId":10,"assistingParticipantIds":[4]}
                """);

        // when
        MatchState state = timeline.before(2500);

        // then
        assertThat(state.of(4)).isEqualTo(participantState(1, 1, 0, 1));
        assertThat(state.of(2)).isEqualTo(participantState(1, 1, 0, 1));
        assertThat(state.of(5)).isEqualTo(participantState(1, 0, 0, 1));
        assertThat(state.of(9)).isEqualTo(participantState(1, 0, 1, 0));
        assertThat(state.of(10)).isEqualTo(participantState(1, 0, 1, 0));
    }

    @Test
    @DisplayName("기준 시각과 같은 시각의 킬은 세지 않는다")
    void before_WhenKillHappenedAtSameTime_ExcludesIt() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"CHAMPION_KILL","timestamp":1000,"killerId":4,"victimId":9}
                """);

        // when & then
        assertThat(timeline.before(1000).of(4).kills()).isZero();
        assertThat(timeline.before(1001).of(4).kills()).isEqualTo(1);
    }

    @Test
    @DisplayName("처치한 쪽이 참가자가 아니면 킬은 아무에게도 주지 않고 데스와 어시스트만 센다")
    void before_WhenKillerIsNotParticipant_CountsOnlyDeathAndAssists() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"CHAMPION_KILL","timestamp":1000,"killerId":0,"victimId":3,"assistingParticipantIds":[7]}
                """);

        // when
        MatchState state = timeline.before(2000);

        // then
        assertThat(state.of(3).deaths()).isEqualTo(1);
        assertThat(state.of(7).assists()).isEqualTo(1);
        assertThat(IntStream.rangeClosed(0, 10).map(participantId -> state.of(participantId).kills()).sum())
                .isZero();
    }

    @Test
    @DisplayName("킬 기록에 나오지 않은 참가자는 모두 0이다")
    void before_WhenParticipantHasNoKillEvents_ReturnsZeros() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"CHAMPION_KILL","timestamp":1000,"killerId":4,"victimId":9}
                """);

        // when
        ParticipantState state = timeline.before(2000).of(1);

        // then
        assertThat(state).isEqualTo(participantState(1, 0, 0, 0));
    }

    @Test
    @DisplayName("기준 시각 이전의 레벨업 중 가장 높은 레벨을 답한다")
    void before_WhenLevelUpsHappenedEarlier_ReturnsHighestLevel() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"LEVEL_UP","timestamp":1000,"participantId":3,"level":2},
                {"type":"LEVEL_UP","timestamp":2000,"participantId":3,"level":3},
                {"type":"LEVEL_UP","timestamp":2500,"participantId":7,"level":2},
                {"type":"LEVEL_UP","timestamp":4000,"participantId":3,"level":4}
                """);

        // when
        MatchState state = timeline.before(3000);

        // then
        assertThat(state.of(3).level()).isEqualTo(3);
        assertThat(state.of(7).level()).isEqualTo(2);
    }

    @Test
    @DisplayName("레벨업 기록이 없는 참가자는 1레벨이다")
    void before_WhenParticipantHasNoLevelUps_ReturnsLevelOne() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"LEVEL_UP","timestamp":1000,"participantId":3,"level":2}
                """);

        // when & then
        assertThat(timeline.before(2000).of(8).level()).isEqualTo(1);
    }

    @Test
    @DisplayName("기준 시각과 같은 시각의 레벨업은 반영하지 않는다")
    void before_WhenLevelUpHappenedAtSameTime_ExcludesIt() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"LEVEL_UP","timestamp":1000,"participantId":3,"level":2}
                """);

        // when & then
        assertThat(timeline.before(1000).of(3).level()).isEqualTo(1);
        assertThat(timeline.before(1001).of(3).level()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("타임라인이 없으면 예외 없이 빈 결과를 돌려준다")
    void read_WhenTimelineIsMissing_ReturnsEmpty(String rawTimeline) {
        // when
        Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, CORE_ITEMS);

        // then
        assertThat(timeline).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"info\":", "{\"info\":{}}", "{\"metadata\":{}}"})
    @DisplayName("JSON이 깨졌거나 frames가 없으면 예외 없이 빈 결과를 돌려준다")
    void read_WhenTimelineIsMalformed_ReturnsEmpty(String rawTimeline) {
        // when
        Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, CORE_ITEMS);

        // then
        assertThat(timeline).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"CHAMPION_KILL\",\"killerId\":4,\"victimId\":9}",
            "{\"type\":\"LEVEL_UP\",\"participantId\":3,\"level\":2}"
    })
    @DisplayName("킬이나 레벨업 기록 하나라도 시각이 없으면 빈 결과를 돌려준다")
    void read_WhenStateEventHasNoTimestamp_ReturnsEmpty(String eventWithoutTimestamp) {
        // given
        String rawTimeline = timelineOf("""
                {"type":"CHAMPION_KILL","timestamp":1000,"killerId":2,"victimId":7},
                """ + eventWithoutTimestamp);

        // when
        Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, CORE_ITEMS);

        // then
        assertThat(timeline).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"CHAMPION_KILL\",\"timestamp\":1500,\"killerId\":4}",
            "{\"type\":\"CHAMPION_KILL\",\"timestamp\":1500,\"victimId\":9}",
            "{\"type\":\"CHAMPION_KILL\",\"timestamp\":1500,\"killerId\":\"4\",\"victimId\":9}",
            "{\"type\":\"CHAMPION_KILL\",\"timestamp\":1500,\"killerId\":4,\"victimId\":9,\"assistingParticipantIds\":[\"x\"]}",
            "{\"type\":\"CHAMPION_KILL\",\"timestamp\":1500.5,\"killerId\":4,\"victimId\":9}",
            "{\"type\":\"LEVEL_UP\",\"timestamp\":1500,\"level\":2}",
            "{\"type\":\"LEVEL_UP\",\"timestamp\":1500,\"participantId\":3}"
    })
    @DisplayName("킬이나 레벨업 기록 하나라도 누구의 기록인지나 값을 알 수 없으면 빈 결과를 돌려준다")
    void read_WhenStateEventHasMissingField_ReturnsEmpty(String incompleteEvent) {
        // given
        String rawTimeline = timelineOf("""
                {"type":"CHAMPION_KILL","timestamp":1000,"killerId":2,"victimId":7},
                """ + incompleteEvent);

        // when
        Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, CORE_ITEMS);

        // then
        assertThat(timeline).isEmpty();
    }

    @Test
    @DisplayName("상태와 무관한 기록은 시각이 없어도 빈 결과로 만들지 않는다")
    void read_WhenUnrelatedEventHasNoTimestamp_ReturnsTimeline() {
        // given
        String rawTimeline = timelineOf("""
                {"type":"CHAMPION_KILL","timestamp":1000,"killerId":2,"victimId":7},
                {"type":"WARD_PLACED","creatorId":2}
                """);

        // when
        Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, CORE_ITEMS);

        // then
        assertThat(timeline).isPresent();
        assertThat(timeline.orElseThrow().before(2000).of(2).kills()).isEqualTo(1);
    }

    @Test
    @DisplayName("기준 시각 직전에 들고 있던 코어를 얻은 순서대로 답한다")
    void before_WhenCoresWereBoughtEarlier_ReturnsHeldCoresInOrder() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3071},
                {"type":"ITEM_PURCHASED","timestamp":150,"participantId":1,"itemId":1036},
                {"type":"ITEM_PURCHASED","timestamp":200,"participantId":1,"itemId":3047},
                {"type":"ITEM_PURCHASED","timestamp":5000,"participantId":1,"itemId":3053}
                """);

        // when
        List<Integer> coreItemIds = timeline.before(3000).of(1).coreItemIds();

        // then
        assertThat(coreItemIds).containsExactly(3071, 3047);
    }

    @Test
    @DisplayName("팔았거나 되돌린 코어는 들고 있는 코어에서 빠진다")
    void before_WhenCoresWereSoldOrUndone_ExcludesThem() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3071},
                {"type":"ITEM_PURCHASED","timestamp":200,"participantId":1,"itemId":3053},
                {"type":"ITEM_SOLD","timestamp":300,"participantId":1,"itemId":3071},
                {"type":"ITEM_PURCHASED","timestamp":400,"participantId":1,"itemId":6610},
                {"type":"ITEM_UNDO","timestamp":500,"participantId":1,"beforeId":6610,"afterId":0}
                """);

        // when
        List<Integer> coreItemIds = timeline.before(1000).of(1).coreItemIds();

        // then
        assertThat(coreItemIds).containsExactly(3053);
    }

    @Test
    @DisplayName("같은 시각에 산 코어는 빠지고 다른 참가자의 코어는 섞이지 않는다")
    void before_WhenCoreBoughtAtSameTimeOrByOthers_ExcludesIt() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"ITEM_PURCHASED","timestamp":500,"participantId":2,"itemId":3053},
                {"type":"ITEM_PURCHASED","timestamp":1000,"participantId":1,"itemId":3071}
                """);

        // when & then
        assertThat(timeline.before(1000).of(1).coreItemIds()).isEmpty();
        assertThat(timeline.before(1000).of(2).coreItemIds()).containsExactly(3053);
        assertThat(timeline.before(1001).of(1).coreItemIds()).containsExactly(3071);
    }

    @Test
    @DisplayName("코어 기록 하나라도 시각이 없으면 빈 결과를 돌려준다")
    void read_WhenCoreItemEventHasNoTimestamp_ReturnsEmpty() {
        // given
        String rawTimeline = timelineOf("""
                {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3053},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071}
                """);

        // when
        Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, CORE_ITEMS);

        // then
        assertThat(timeline).isEmpty();
    }

    @Test
    @DisplayName("코어가 아닌 아이템 기록은 시각이 없어도 빈 결과로 만들지 않는다")
    void read_WhenNonCoreItemEventHasNoTimestamp_ReturnsTimeline() {
        // given
        String rawTimeline = timelineOf("""
                {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3053},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036}
                """);

        // when
        Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, CORE_ITEMS);

        // then
        assertThat(timeline).isPresent();
        assertThat(timeline.orElseThrow().before(200).of(1).coreItemIds()).containsExactly(3053);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"ITEM_PURCHASED\",\"timestamp\":\"abc\",\"participantId\":1,\"itemId\":3071}",
            "{\"type\":\"ITEM_PURCHASED\",\"timestamp\":200,\"itemId\":3071}"
    })
    @DisplayName("코어 기록의 값이 타입과 맞지 않거나 참가자 번호가 없으면 빈 결과를 돌려준다")
    void read_WhenCoreItemEventIsMalformed_ReturnsEmpty(String malformedEvent) {
        // given
        String rawTimeline = timelineOf("""
                {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3053},
                """ + malformedEvent);

        // when
        Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, CORE_ITEMS);

        // then
        assertThat(timeline).isEmpty();
    }

    @Test
    @DisplayName("여러 프레임에 걸쳐 시각이 뒤섞여 있어도 시각 순서대로 보유를 따라간다")
    void before_WhenFramesAreOutOfOrder_FollowsTimestampOrder() {
        // given
        String rawTimeline = """
                {"info":{"frames":[
                  {"events":[{"type":"ITEM_PURCHASED","timestamp":300,"participantId":1,"itemId":3053}]},
                  {"events":[{"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3071}]}
                ]}}
                """;

        // when
        List<Integer> coreItemIds = reader.read(rawTimeline, CORE_ITEMS).orElseThrow()
                .before(1000).of(1).coreItemIds();

        // then
        assertThat(coreItemIds).containsExactly(3071, 3053);
    }

    @Test
    @DisplayName("같은 시각의 구매와 되돌리기는 타임라인에 나온 순서대로 처리한다")
    void before_WhenPurchaseAndUndoShareTimestamp_KeepsTimelineOrder() {
        // given
        MatchStateTimeline timeline = read("""
                {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3071},
                {"type":"ITEM_UNDO","timestamp":100,"participantId":1,"beforeId":3071,"afterId":0}
                """);

        // when & then
        assertThat(timeline.before(200).of(1).coreItemIds()).isEmpty();
    }

    @Test
    @DisplayName("구매 순서 계산기가 낸 시각 직후에는 그 코어를 들고 있고 직전에는 들고 있지 않다")
    void before_WhenAskedAroundCalculatedAcquiredTimes_AgreesWithCalculator() throws Exception {
        // given
        String rawTimeline = timelineOf("""
                {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3071},
                {"type":"ITEM_PURCHASED","timestamp":200,"participantId":1,"itemId":3053},
                {"type":"ITEM_SOLD","timestamp":300,"participantId":1,"itemId":3071},
                {"type":"ITEM_PURCHASED","timestamp":400,"participantId":1,"itemId":3071}
                """);
        List<CoreItemAcquisition> acquisitions = new CoreItemPurchaseOrderCalculator()
                .calculateWithAcquiredTimes(
                        new ObjectMapper().readValue(rawTimeline, MatchTimelineResponse.class),
                        1,
                        List.of(3071, 3053),
                        CORE_ITEMS
                )
                .orElseThrow();
        MatchStateTimeline timeline = reader.read(rawTimeline, CORE_ITEMS).orElseThrow();

        // when & then
        assertThat(acquisitions).hasSize(2);
        for (CoreItemAcquisition acquisition : acquisitions) {
            assertThat(timeline.before(acquisition.gameTimeMs()).of(1).coreItemIds())
                    .doesNotContain(acquisition.itemId());
            assertThat(timeline.before(acquisition.gameTimeMs() + 1).of(1).coreItemIds())
                    .contains(acquisition.itemId());
        }
    }

    private MatchStateTimeline read(String events) {
        return reader.read(timelineOf(events), CORE_ITEMS).orElseThrow();
    }

    private ParticipantState participantState(int level, int kills, int deaths, int assists) {
        return new ParticipantState(level, kills, deaths, assists, CoreItemInventory.empty());
    }

    private String timelineOf(String events) {
        return "{\"info\":{\"frames\":[{\"events\":[" + events + "]}]}}";
    }
}
