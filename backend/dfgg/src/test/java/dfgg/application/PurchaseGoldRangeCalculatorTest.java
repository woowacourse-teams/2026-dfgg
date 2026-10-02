package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.PurchaseGoldRangeCalculator;
import dfgg.domain.match.GoldRange;
import dfgg.domain.match.PassiveGoldAssumption;
import dfgg.domain.match.ParticipantItemEvent;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PurchaseGoldRangeCalculatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ParticipantItemEventExtractor eventExtractor = new ParticipantItemEventExtractor(mapper);
    private final PurchaseGoldRangeCalculator calculator = new PurchaseGoldRangeCalculator(mapper);

    @Test
    void 모든_아이템_거래를_반영해_구매_직전의_기본_골드_범위를_구한다() {
        String raw = timeline(500, 900, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":2055,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var ranges = calculator.calculate("KR_TEST", raw, events, catalog());

        assertThat(ranges.get(events.get(1))).isEqualTo(new GoldRange(425, 1200));
        assertThat(ranges.get(events.getFirst())).isEqualTo(new GoldRange(500, 1275));
    }

    @Test
    void 판매_수익_구간과_UNDO의_관측_goldGain을_반영한다() {
        String raw = timeline(500, 550, """
                {"type":"ITEM_SOLD","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_UNDO","participantId":1,"beforeId":0,"afterId":1036,
                 "goldGain":-100,"timestamp":101},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":102},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":103}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        assertThat(calculator.calculate("KR_TEST", raw, events, catalog()).get(events.getLast()))
                .isEqualTo(new GoldRange(400, 750));
    }

    @Test
    void 프레임_골드나_거래_가격이_없으면_그_프레임의_Range를_만들지_않는다() {
        String missingGold = timeline(null, 900, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100}
                """);
        String missingPrice = timeline(500, 900, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":9999,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101}
                """);

        assertThat(calculator.calculate("KR_TEST", missingGold,
                eventExtractor.extract("KR_TEST", missingGold), catalog())).isEmpty();
        assertThat(calculator.calculate("KR_TEST", missingPrice,
                eventExtractor.extract("KR_TEST", missingPrice), catalog())).isEmpty();
    }

    @Test
    void 비음수_수입으로_설명할_수_없는_프레임은_제외한다() {
        String raw = timeline(500, 0, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100}
                """);

        assertThat(calculator.calculate("KR_TEST", raw,
                eventExtractor.extract("KR_TEST", raw), catalog())).isEmpty();
    }

    @Test
    void 시각_정렬과_달라도_프레임의_원본_이벤트_순서로_계산한다() {
        String raw = timeline(500, 900, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":2055,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var ranges = calculator.calculate("KR_TEST", raw, events, catalog());

        assertThat(events.getFirst().sourceEventIndex()).isEqualTo(1);
        assertThat(ranges.get(events.getFirst())).isEqualTo(new GoldRange(425, 1200));
        assertThat(ranges.get(events.getLast())).isEqualTo(new GoldRange(500, 1275));
    }

    @Test
    void goldGain이_없는_UNDO는_임의의_금액으로_채우지_않는다() {
        String raw = timeline(500, 900, """
                {"type":"ITEM_UNDO","participantId":1,"beforeId":1036,"afterId":0,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":101}
                """);

        assertThat(calculator.calculate("KR_TEST", raw,
                eventExtractor.extract("KR_TEST", raw), catalog())).isEmpty();
    }

    @Test
    void 원천_이벤트와_다른_Timeline을_조합하면_실패한다() {
        String raw = timeline(500, 900, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100}
                """);
        String changed = raw.replace("\"itemId\":1036", "\"itemId\":1037");

        assertThatThrownBy(() -> calculator.calculate("KR_TEST", changed,
                eventExtractor.extract("KR_TEST", raw), catalog()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("원천 이벤트");
    }

    @Test
    void 자연_골드와_본인_킬_가정을_기본_Range와_분리해_보정한다() {
        String raw = timedTimeline(500, 900, 70_000, 130_000, """
                {"type":"CHAMPION_KILL","killerId":1,"bounty":100,
                 "shutdownBounty":0,"timestamp":90000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100000},
                {"type":"CHAMPION_KILL","killerId":1,"bounty":50,
                 "shutdownBounty":0,"timestamp":110000}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithIncomeAssumptions("KR_TEST", raw, events, catalog(),
                new PassiveGoldAssumption(65_000, 204, 2)).get(events.getFirst());

        assertThat(result.base()).isEqualTo(new GoldRange(500, 1200));
        assertThat(result.passiveAssumption()).contains(new GoldRange(559, 1141));
        assertThat(result.passiveAndKillAssumption()).contains(new GoldRange(659, 1091));
    }

    @Test
    void 수입_가정이_가능한_총수입을_넘으면_기본_Range만_남긴다() {
        String raw = timedTimeline(500, 500, 70_000, 130_000, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100000}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithIncomeAssumptions("KR_TEST", raw, events, catalog(),
                new PassiveGoldAssumption(0, 1000, 0)).get(events.getFirst());

        assertThat(result.base()).isEqualTo(new GoldRange(500, 800));
        assertThat(result.passiveAssumption()).isEmpty();
        assertThat(result.passiveAndKillAssumption()).isEmpty();
    }

    @Test
    void 킬_지급액이_누락되면_자연_골드_보정까지만_유지한다() {
        String raw = timedTimeline(500, 900, 70_000, 130_000, """
                {"type":"CHAMPION_KILL","killerId":1,"bounty":100,"timestamp":90000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100000}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithIncomeAssumptions("KR_TEST", raw, events, catalog(),
                new PassiveGoldAssumption(65_000, 204, 2)).get(events.getFirst());

        assertThat(result.passiveAssumption()).contains(new GoldRange(559, 1141));
        assertThat(result.passiveAndKillAssumption()).isEmpty();
    }

    @Test
    void 구매와_동일한_timestamp의_킬은_전후에_배치하지_않는다() {
        String raw = timedTimeline(500, 900, 70_000, 130_000, """
                {"type":"CHAMPION_KILL","killerId":1,"timestamp":100000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100000}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithIncomeAssumptions("KR_TEST", raw, events, catalog(),
                new PassiveGoldAssumption(65_000, 204, 2)).get(events.getFirst());

        assertThat(result.passiveAndKillAssumption()).isEqualTo(result.passiveAssumption());
    }

    @Test
    void 프레임_밖의_킬은_시점_보정에_쓰지_않는다() {
        String raw = timedTimeline(500, 900, 70_000, 130_000, """
                {"type":"CHAMPION_KILL","killerId":1,"bounty":100,
                 "shutdownBounty":0,"timestamp":140000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100000}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithIncomeAssumptions("KR_TEST", raw, events, catalog(),
                new PassiveGoldAssumption(65_000, 204, 2)).get(events.getFirst());

        assertThat(result.passiveAssumption()).isPresent();
        assertThat(result.passiveAndKillAssumption()).isEmpty();
    }

    @Test
    void 프레임_시각이_없으면_기본_Range만_남긴다() {
        String raw = timeline(500, 900, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100000}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithIncomeAssumptions("KR_TEST", raw, events, catalog(),
                new PassiveGoldAssumption(65_000, 204, 2)).get(events.getFirst());

        assertThat(result.base()).isEqualTo(new GoldRange(500, 1200));
        assertThat(result.passiveAssumption()).isEmpty();
    }

    @Test
    void totalGold_증가량과_거래액이_양립하면_구매_직전_범위를_좁힌다() {
        String raw = timelineWithTotalGold(500, 900, 1_000, 1_500, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithTotalGold("KR_TEST", raw, events, catalog()).get(events.getFirst());

        assertThat(result.base()).isEqualTo(new GoldRange(500, 1_200));
        assertThat(result.totalGoldRefinement()).contains(new GoldRange(500, 1_000));
    }

    @Test
    void totalGold는_구매_전_거래액의_가능한_범위도_좁힌다() {
        String raw = timelineWithTotalGold(500, 900, 1_000, 1_300, """
                {"type":"ITEM_SOLD","participantId":1,"itemId":1037,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithTotalGold("KR_TEST", raw, events, catalog()).get(events.getLast());

        assertThat(result.base()).isEqualTo(new GoldRange(500, 1_200));
        assertThat(result.totalGoldRefinement()).contains(new GoldRange(600, 1_000));
    }

    @Test
    void totalGold가_거래_구간과_모순되면_기본_Range를_유지한다() {
        String raw = timelineWithTotalGold(500, 900, 1_000, 1_100, """
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100}
                """);
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithTotalGold("KR_TEST", raw, events, catalog()).get(events.getFirst());

        assertThat(result.base()).isEqualTo(new GoldRange(500, 1_200));
        assertThat(result.totalGoldRefinement()).isEmpty();
    }

    @Test
    void 자연_골드와_킬_보정은_totalGold로_좁힌_범위에_적용한다() {
        String raw = timedTimeline(500, 900, 70_000, 130_000, """
                {"type":"CHAMPION_KILL","killerId":1,"bounty":100,
                 "shutdownBounty":0,"timestamp":90000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100000},
                {"type":"CHAMPION_KILL","killerId":1,"bounty":50,
                 "shutdownBounty":0,"timestamp":110000}
                """).replace("\"currentGold\":500", "\"currentGold\":500,\"totalGold\":1000")
                .replace("\"currentGold\":900", "\"currentGold\":900,\"totalGold\":1500");
        List<ParticipantItemEvent> events = eventExtractor.extract("KR_TEST", raw);

        var result = calculator.refineWithIncomeAssumptions("KR_TEST", raw, events, catalog(),
                new PassiveGoldAssumption(65_000, 204, 2)).get(events.getFirst());

        assertThat(result.base()).isEqualTo(new GoldRange(500, 1_200));
        assertThat(result.totalGoldRefinement()).contains(new GoldRange(500, 1_000));
        assertThat(result.passiveAssumption()).contains(new GoldRange(559, 941));
        assertThat(result.passiveAndKillAssumption()).contains(new GoldRange(659, 891));
    }

    private ItemResponse catalog() {
        return new ItemResponse("16.18", "16.18.1", Map.of(
                "1036", item(300), "1037", item(200), "2055", item(75)));
    }

    private ItemData item(int total) {
        return new ItemData("test", List.of(), List.of(), List.of(), Map.of("11", true), false, 1,
                new ItemData.Gold(total, total, true), null, true, false);
    }

    private String timeline(Integer startGold, Integer endGold, String events) {
        String start = goldPayload(startGold);
        String end = goldPayload(endGold);
        return "{\"metadata\":{\"matchId\":\"KR_TEST\"},\"info\":{\"frames\":["
                + "{\"participantFrames\":{\"1\":" + start + "},\"events\":[]},"
                + "{\"participantFrames\":{\"1\":" + end + "},\"events\":[" + events + "]}]}}";
    }

    private String timedTimeline(Integer startGold, Integer endGold, long startMs, long endMs, String events) {
        String start = goldPayload(startGold);
        String end = goldPayload(endGold);
        return "{\"metadata\":{\"matchId\":\"KR_TEST\"},\"info\":{\"frames\":["
                + "{\"timestamp\":" + startMs + ",\"participantFrames\":{\"1\":" + start + "},\"events\":[]},"
                + "{\"timestamp\":" + endMs + ",\"participantFrames\":{\"1\":" + end
                + "},\"events\":[" + events + "]}]}}";
    }

    private String timelineWithTotalGold(int startGold, int endGold,
                                         int startTotal, int endTotal, String events) {
        String start = "{\"currentGold\":" + startGold + ",\"totalGold\":" + startTotal + "}";
        String end = "{\"currentGold\":" + endGold + ",\"totalGold\":" + endTotal + "}";
        return "{\"metadata\":{\"matchId\":\"KR_TEST\"},\"info\":{\"frames\":["
                + "{\"participantFrames\":{\"1\":" + start + "},\"events\":[]},"
                + "{\"participantFrames\":{\"1\":" + end + "},\"events\":[" + events + "]}]}}";
    }

    private String goldPayload(Integer gold) {
        if (gold == null) {
            return "{}";
        }
        return "{\"currentGold\":" + gold + "}";
    }
}
