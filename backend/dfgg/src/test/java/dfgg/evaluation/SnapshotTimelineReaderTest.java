package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.CoreItemPurchaseOrderCalculator;
import dfgg.application.match.MatchStateTimelineReader;
import dfgg.application.recommend.v3.GameSituation;
import dfgg.domain.match.CoreItemAcquisition;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotTimelineReaderTest {

    private static final Set<Integer> CORE_ITEMS = Set.of(3071, 3053, 3089);

    private final SnapshotTimelineReader reader = new SnapshotTimelineReader(
            new MatchStateTimelineReader(new ObjectMapper()), new CoreItemPurchaseOrderCalculator(),
            new ObjectMapper(), CORE_ITEMS);

    private final NormalizedMatchParticipant me = participant("me", 1, 157, 100, "MIDDLE", List.of(3071, 3053));
    private final List<NormalizedMatchParticipant> match = List.of(
            me, participant("enemy", 6, 103, 200, "MIDDLE", List.of(3089)));

    private static NormalizedMatchParticipant participant(
            String puuid, int participantId, int championId, int teamId, String position, List<Integer> order
    ) {
        return new NormalizedMatchParticipant(
                puuid, participantId, championId, teamId, position, "EMERALD", true, order, order, true);
    }

    /** 나(1번)는 100에 3071, 400에 3053을 샀다. 그 사이 200에 상대(6번)를 잡았고 300에 상대가 3089를 샀다. */
    private static final String RAW_TIMELINE = """
            {"info":{"frames":[{"events":[
              {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3071},
              {"type":"CHAMPION_KILL","timestamp":200,"killerId":1,"victimId":6},
              {"type":"ITEM_PURCHASED","timestamp":300,"participantId":6,"itemId":3089},
              {"type":"ITEM_PURCHASED","timestamp":400,"participantId":1,"itemId":3053}
            ]}]}}
            """;

    @Test
    @DisplayName("원본 타임라인에서 참가자가 Core를 얻은 시각을 구매 순서대로 읽는다")
    void acquisitionsOf_WhenTimelineReadable_ReturnsAcquiredTimesInPurchaseOrder() {
        // when
        var acquisitions = reader.read(RAW_TIMELINE).acquisitionsOf(me);

        // then
        assertThat(acquisitions).contains(List.of(
                new CoreItemAcquisition(3071, 100L), new CoreItemAcquisition(3053, 400L)));
    }

    @Test
    @DisplayName("읽은 상태 기록과 구매 시각으로 스냅샷에 구매 직전의 상황이 채워진다")
    void read_WhenTimelineReadable_LetsBuilderFillSituationBeforeEachPurchase() {
        // given
        SnapshotTimelineReader.SnapshotTimelines timelines = reader.read(RAW_TIMELINE);
        SnapshotQueryBuilder builder = new SnapshotQueryBuilder(CORE_ITEMS);

        // when
        List<SnapshotQuery> snapshots = builder.buildWithSituation(
                match, "me", "16.17", timelines.states(), timelines.acquisitionsOf(me));

        // then: 첫 Core(100) 직전에는 아무 일도 없었고, 둘째 Core(400) 직전에는 킬 하나와 상대의 3089가 있다
        GameSituation.Known beforeFirstCore = (GameSituation.Known) snapshots.get(0).query().situation();
        assertThat(beforeFirstCore.me().kills()).isZero();
        assertThat(beforeFirstCore.enemies().get(0).coreItemIds()).isEmpty();

        GameSituation.Known beforeSecondCore = (GameSituation.Known) snapshots.get(1).query().situation();
        assertThat(beforeSecondCore.me().kills()).isEqualTo(1);
        assertThat(beforeSecondCore.enemies().get(0).coreItemIds()).containsExactly(3089L);
    }

    @Test
    @DisplayName("타임라인 원본이 없으면 상태 기록도 구매 시각도 비어 있다")
    void read_WhenRawTimelineMissing_ReturnsNothing() {
        // when
        SnapshotTimelineReader.SnapshotTimelines timelines = reader.read(null);

        // then
        assertThat(timelines.found()).isFalse();
        assertThat(timelines.states()).isEmpty();
        assertThat(timelines.acquisitionsOf(me)).isEmpty();
    }

    @Test
    @DisplayName("타임라인 원본을 읽을 수 없어도 예외 없이 빈 결과다 — 한 경기 때문에 내보내기가 멈추지 않는다")
    void read_WhenRawTimelineUnreadable_ReturnsNothingWithoutThrowing() {
        // when
        SnapshotTimelineReader.SnapshotTimelines timelines = reader.read("{not json");

        // then
        assertThat(timelines.found()).isTrue();
        assertThat(timelines.states()).isEmpty();
        assertThat(timelines.acquisitionsOf(me)).isEmpty();
    }

    @Test
    @DisplayName("샀다가 판 Core가 있으면, 그 사이의 스냅샷은 실제 보유와 구매 이력이 다르다고 센다")
    void countHeldCoreMismatches_WhenCoreWasSoldLater_CountsSnapshotsBetweenPurchaseAndSale() {
        // given: 100에 3071을 사고 400에 3053을 산 뒤 500에 3071을 팔았다. 끝까지 남은 Core는 3053뿐이다
        String soldLater = """
                {"info":{"frames":[{"events":[
                  {"type":"ITEM_PURCHASED","timestamp":100,"participantId":1,"itemId":3071},
                  {"type":"ITEM_PURCHASED","timestamp":400,"participantId":1,"itemId":3053},
                  {"type":"ITEM_SOLD","timestamp":500,"participantId":1,"itemId":3071}
                ]}]}}
                """;
        NormalizedMatchParticipant seller = participant("me", 1, 157, 100, "MIDDLE", List.of(3053));
        List<NormalizedMatchParticipant> sellerMatch = List.of(
                seller, participant("enemy", 6, 103, 200, "MIDDLE", List.of(3089)));
        SnapshotTimelineReader.SnapshotTimelines timelines = reader.read(soldLater);
        List<CoreItemAcquisition> acquisitions = timelines.acquisitionsOf(seller).orElseThrow();
        List<SnapshotQuery> snapshots = new SnapshotQueryBuilder(CORE_ITEMS).buildWithSituation(
                sellerMatch, "me", "16.17", timelines.states(), java.util.Optional.of(acquisitions));

        // when: 3053을 사기 직전(400)에 실제로는 3071을 들고 있었지만, 구매 이력에는 아무것도 없다
        long mismatches = timelines.countHeldCoreMismatches(seller, acquisitions, snapshots);

        // then
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).query().purchasedItemIds()).isEmpty();
        assertThat(mismatches).isEqualTo(1);
    }

    @Test
    @DisplayName("산 Core를 끝까지 들고 있으면 실제 보유와 구매 이력이 같다")
    void countHeldCoreMismatches_WhenEveryCoreIsKept_CountsNothing() {
        // given
        SnapshotTimelineReader.SnapshotTimelines timelines = reader.read(RAW_TIMELINE);
        List<CoreItemAcquisition> acquisitions = timelines.acquisitionsOf(me).orElseThrow();
        List<SnapshotQuery> snapshots = new SnapshotQueryBuilder(CORE_ITEMS).buildWithSituation(
                match, "me", "16.17", timelines.states(), java.util.Optional.of(acquisitions));

        // when & then
        assertThat(timelines.countHeldCoreMismatches(me, acquisitions, snapshots)).isZero();
    }
}
