package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dfgg.application.match.CoreItemPurchaseOrderCalculator;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurchaseSituationReaderTest {

    private static final int BLUE = 100;
    private static final int RED = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PurchaseSituationReader reader = new PurchaseSituationReader(
            objectMapper, new CoreItemPurchaseOrderCalculator(), List.of(3071, 3053, 6610));

    private final NormalizedMatchParticipant me = participant(1, BLUE, List.of(3071, 3053));
    private final List<NormalizedMatchParticipant> matchParticipants = List.of(
            me,
            participant(2, BLUE, List.of()),
            participant(6, RED, List.of()),
            participant(7, RED, List.of()));

    @Test
    @DisplayName("Core마다 구매 시각(분), 그 분 프레임의 팀 골드 차이(내 팀 − 상대 팀), 본인 골드를 낸다")
    void read_WhenCoresBought_ReturnsSituationAtEachPurchase() {
        // given
        String timeline = timeline(
                frame(0, gold(500, 500, 500, 500)),
                frame(60_000, gold(1000, 900, 800, 700), purchase(70_000, 3071)),
                frame(120_000, gold(2000, 1800, 2100, 2000), purchase(130_000, 3053)));

        // when
        List<PurchaseSituation> situations = reader.read(timeline, me, matchParticipants);

        // then
        assertThat(situations).containsExactly(
                new PurchaseSituation(70_000 / 60_000.0, 400, 1000),
                new PurchaseSituation(130_000 / 60_000.0, -300, 2000));
    }

    @Test
    @DisplayName("프레임에 골드가 빠진 참가자가 있으면 팀 골드 차이만 모름이다")
    void read_WhenSomeoneHasNoGold_LeavesTeamGoldDiffUnknown() {
        // given
        Map<Integer, Integer> missingRed = gold(1000, 900, 800, 700);
        missingRed.put(7, null);
        String timeline = timeline(
                frame(0, gold(500, 500, 500, 500)),
                frame(60_000, missingRed, purchase(70_000, 3071), purchase(80_000, 3053)));

        // when
        List<PurchaseSituation> situations = reader.read(timeline, me, matchParticipants);

        // then
        assertThat(situations.getFirst()).isEqualTo(new PurchaseSituation(70_000 / 60_000.0, null, 1000));
    }

    @Test
    @DisplayName("마지막 프레임보다 늦은 구매는 마지막 프레임 골드를 쓴다")
    void read_WhenPurchaseIsAfterLastFrame_UsesLastFrame() {
        // given
        String timeline = timeline(
                frame(0, gold(500, 500, 500, 500), purchase(10_000, 3071)),
                frame(60_000, gold(1000, 900, 800, 700), purchase(185_000, 3053)));

        // when
        List<PurchaseSituation> situations = reader.read(timeline, me, matchParticipants);

        // then
        assertThat(situations.get(1)).isEqualTo(new PurchaseSituation(185_000 / 60_000.0, 400, 1000));
    }

    @Test
    @DisplayName("타임라인이 없으면 구매 순서 길이만큼 모두 모름이다")
    void read_WhenTimelineMissing_ReturnsUnknownForEveryStep() {
        // when
        List<PurchaseSituation> situations = reader.read(null, me, matchParticipants);

        // then
        assertThat(situations).containsExactly(PurchaseSituation.unknown(), PurchaseSituation.unknown());
    }

    @Test
    @DisplayName("다시 계산한 구매 순서가 저장된 순서와 다르면 모두 모름이다")
    void read_WhenRecalculatedOrderDiffers_ReturnsUnknownForEveryStep() {
        // given
        String timeline = timeline(
                frame(0, gold(500, 500, 500, 500), purchase(10_000, 3053)),
                frame(60_000, gold(1000, 900, 800, 700), purchase(70_000, 3071)));

        // when
        List<PurchaseSituation> situations = reader.read(timeline, me, matchParticipants);

        // then
        assertThat(situations).containsExactly(PurchaseSituation.unknown(), PurchaseSituation.unknown());
    }

    private NormalizedMatchParticipant participant(int participantId, int teamId, List<Integer> coreOrder) {
        return new NormalizedMatchParticipant(
                "puuid-" + participantId, participantId, 100 + participantId, teamId, "MIDDLE", "GOLD",
                true, coreOrder, coreOrder, true);
    }

    /** 참가자 1, 2(파랑), 6, 7(빨강) 순서의 totalGold. */
    private Map<Integer, Integer> gold(Integer p1, Integer p2, Integer p6, Integer p7) {
        Map<Integer, Integer> gold = new LinkedHashMap<>();
        gold.put(1, p1);
        gold.put(2, p2);
        gold.put(6, p6);
        gold.put(7, p7);
        return gold;
    }

    private ObjectNode purchase(long timestamp, int itemId) {
        return objectMapper.createObjectNode()
                .put("timestamp", timestamp)
                .put("type", "ITEM_PURCHASED")
                .put("participantId", 1)
                .put("itemId", itemId);
    }

    private ObjectNode frame(long timestamp, Map<Integer, Integer> gold, ObjectNode... events) {
        ObjectNode frame = objectMapper.createObjectNode().put("timestamp", timestamp);
        ObjectNode participantFrames = frame.putObject("participantFrames");
        gold.forEach((participantId, totalGold) -> {
            ObjectNode participantFrame = participantFrames.putObject(String.valueOf(participantId));
            participantFrame.put("participantId", participantId);
            participantFrame.put("totalGold", totalGold);
        });
        ArrayNode eventArray = frame.putArray("events");
        for (ObjectNode event : events) {
            eventArray.add(event);
        }
        return frame;
    }

    private String timeline(ObjectNode... frames) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode info = root.putObject("info");
        info.put("frameInterval", 60_000);
        ArrayNode frameArray = info.putArray("frames");
        for (ObjectNode frame : frames) {
            frameArray.add(frame);
        }
        return root.toString();
    }
}
