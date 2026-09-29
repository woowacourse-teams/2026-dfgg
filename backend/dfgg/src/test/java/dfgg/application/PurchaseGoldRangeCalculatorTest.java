package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.PurchaseGoldRangeCalculator;
import dfgg.domain.match.GoldRange;
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

    private String goldPayload(Integer gold) {
        if (gold == null) {
            return "{}";
        }
        return "{\"currentGold\":" + gold + "}";
    }
}
