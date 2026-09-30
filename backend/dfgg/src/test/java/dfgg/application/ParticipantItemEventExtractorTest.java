package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.domain.match.ParticipantItemEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParticipantItemEventExtractorTest {

    private final ParticipantItemEventExtractor extractor = new ParticipantItemEventExtractor(new ObjectMapper());

    @Test
    void 전체_아이템_이벤트를_추출하고_참가자별_두_순번을_독립적으로_부여한다() throws Exception {
        var result = extractor.extract("KR_TEST", timeline("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"CHAMPION_KILL","timestamp":110},
                {"type":"ITEM_PURCHASED","participantId":2,"itemId":1055,"timestamp":100},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":200},
                {"type":"ITEM_UNDO","participantId":1,"beforeId":3071,"afterId":0,
                 "timestamp":300,"goldGain":425},
                {"type":"ITEM_SOLD","participantId":1,"itemId":1036,"timestamp":400},
                {"type":"ITEM_FUTURE","participantId":1,"timestamp":500,"newField":{"x":1}}
                """));

        var first = result.stream().filter(event -> event.participantId() == 1).toList();
        assertThat(first).extracting(ParticipantItemEvent::eventOrder).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(first).extracting(ParticipantItemEvent::purchaseOrder).containsExactly(1, null, 2, null, null, null);
        assertThat(first).extracting(ParticipantItemEvent::sourceEventIndex).containsExactly(0, 3, 4, 5, 6, 7);
        assertThat(first.get(3).beforeItemId()).isEqualTo(3071);
        assertThat(first.get(3).afterItemId()).isZero();
        assertThat(new ObjectMapper().readTree(first.get(3).sourcePayload()).get("goldGain").asInt()).isEqualTo(425);
        assertThat(new ObjectMapper().readTree(first.get(5).sourcePayload()).at("/newField/x").asInt()).isEqualTo(1);
        var second = result.stream().filter(event -> event.participantId() == 2).toList();
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().eventOrder()).isEqualTo(1);
        assertThat(second.getFirst().purchaseOrder()).isEqualTo(1);
        assertThat(result).allSatisfy(event -> {
            assertThat(event.matchId()).isEqualTo("KR_TEST");
            assertThat(event.normalizationVersion()).isEqualTo("item-events-v1");
        });
    }

    @Test
    void 시간순으로_정렬하되_동일_시각은_프레임과_이벤트_위치를_유지한다() {
        var result = extractor.extract("KR_TEST", """
                {"info":{"frames":[
                    {"events":[
                        {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":200},
                        {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":300}
                    ]},
                    {"events":[
                        {"type":"ITEM_PURCHASED","participantId":1,"itemId":1055,"timestamp":100},
                        {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":300},
                        {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":300}
                    ]}
                ]}}
                """);

        assertThat(result).extracting(ParticipantItemEvent::itemId).containsExactly(1055, 1036, 1036, 3071, 1037);
        assertThat(result).extracting(ParticipantItemEvent::purchaseOrder).containsExactly(1, 2, null, 3, 4);
        assertThat(result).extracting(ParticipantItemEvent::sourceFrameIndex).containsExactly(1, 0, 0, 1, 1);
        // 같은 시각의 조합 파괴와 독립 구매를 합치거나 제거하지 않는다.
        assertThat(result).hasSize(5);
    }

    @Test
    void 시간이_누락된_참가자는_원본_순서를_보존하고_시간을_만들지_않는다() {
        var result = extractor.extract("KR_TEST", timeline("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":200},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":2,"itemId":1037,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":2,"itemId":1055,"timestamp":100}
                """));

        assertThat(result.stream().filter(event -> event.participantId() == 1))
                .extracting(ParticipantItemEvent::gameTimeMs).containsExactly(200L, null, 100L);
        assertThat(result.stream().filter(event -> event.participantId() == 2))
                .extracting(ParticipantItemEvent::gameTimeMs).containsExactly(100L, 200L);
    }

    @Test
    void 반복된_재료_구매와_이후_취소된_구매도_원천_로그에_유지한다() {
        var result = extractor.extract("KR_TEST", timeline("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101},
                {"type":"ITEM_UNDO","participantId":1,"beforeId":1036,"afterId":0,"timestamp":102},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":103}
                """));
        assertThat(result).extracting(ParticipantItemEvent::purchaseOrder).containsExactly(1, 2, null, 3);
        assertThat(result).extracting(ParticipantItemEvent::itemId).containsExactly(1036, 1036, null, 1037);
    }

    @Test
    void 참가자_0번의_구매는_제외하고_실제_참가자의_원천_위치와_구매_순서를_유지한다() {
        var result = extractor.extract("KR_TEST", timeline("""
                {"type":"ITEM_PURCHASED","participantId":0,"itemId":3865,"timestamp":0},
                {"type":"ITEM_PURCHASED","participantId":0,"itemId":3865,"timestamp":0},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":200}
                """));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ParticipantItemEvent::participantId).containsOnly(1);
        assertThat(result).extracting(ParticipantItemEvent::sourceEventIndex).containsExactly(2, 3);
        assertThat(result).extracting(ParticipantItemEvent::purchaseOrder).containsExactly(1, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"ITEM_PURCHASED\",\"itemId\":1036}",
            "{\"type\":\"ITEM_PURCHASED\",\"participantId\":1}",
            "{\"type\":\"ITEM_PURCHASED\",\"participantId\":1,\"itemId\":0}",
            "{\"type\":\"ITEM_PURCHASED\",\"participantId\":1,\"itemId\":\"1036\"}",
            "{\"type\":\"ITEM_PURCHASED\",\"participantId\":1.5,\"itemId\":1036}",
            "{\"type\":\"ITEM_SOLD\",\"participantId\":1,\"timestamp\":-1}",
            "{\"type\":\"ITEM_SOLD\",\"participantId\":1,\"timestamp\":\"100\"}",
            "{\"type\":\"ITEM_UNDO\",\"participantId\":1,\"beforeId\":2147483648}",
            "{\"participantId\":1}",
            "null"
    })
    void 잘못된_이벤트를_조용히_누락하거나_기본값으로_채우지_않는다(String event) {
        assertThatThrownBy(() -> extractor.extract("KR_TEST", timeline(event)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("프레임 인덱스 0")
                .hasMessageContaining("이벤트 인덱스 0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "null", "{}", "{\"info\":{\"frames\":{}}}",
            "{\"info\":{\"frames\":[{}]}}", "{\"info\":{\"frames\":[{\"events\":{}}]}}",
            "{\"info\":{\"frames\":[]}} {}"})
    void 손상되거나_누락된_원본_구조는_실패한다(String rawData) {
        assertThatThrownBy(() -> extractor.extract("KR_TEST", rawData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("타임라인");
    }

    @Test
    void 빈_이벤트_배열은_정상적으로_빈_목록을_반환한다() {
        assertThat(extractor.extract("KR_TEST", timeline(""))).isEmpty();
    }

    @Test
    void 입력_경기와_원본_경기_ID가_다르면_실패한다() {
        assertThatThrownBy(() -> extractor.extract("KR_TEST",
                "{\"metadata\":{\"matchId\":\"KR_OTHER\"},\"info\":{\"frames\":[]}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 실제_16_18_조합_구매의_재료_제거와_원본_위치를_보존한다() throws Exception {
        String rawData;
        try (var fixture = getClass().getResourceAsStream("/timeline/participant-item-events-16.18.json")) {
            assertThat(fixture).isNotNull();
            rawData = new String(fixture.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var result = extractor.extract("KR_8391829272", rawData);
        var combine = result.stream()
                .filter(event -> event.participantId() == 4 && Long.valueOf(1270802L).equals(event.gameTimeMs()))
                .toList();
        assertThat(combine).extracting(ParticipantItemEvent::eventType)
                .containsExactly("ITEM_DESTROYED", "ITEM_DESTROYED", "ITEM_DESTROYED", "ITEM_PURCHASED");
        assertThat(combine).extracting(ParticipantItemEvent::itemId).containsExactly(1038, 1037, 1018, 3031);
        assertThat(combine).extracting(ParticipantItemEvent::sourceEventIndex).containsExactly(11, 12, 13, 14);
        assertThat(combine).allSatisfy(event -> assertThat(event.sourceFrameIndex()).isEqualTo(22));

        var mapper = new ObjectMapper();
        var frames = mapper.readTree(rawData).path("info").path("frames");
        long originalItemCount = frames.get(22).path("events").findValuesAsText("type").stream()
                .filter(type -> type.startsWith("ITEM_")).count();
        assertThat(result).hasSize((int) originalItemCount);
        for (var event : result) {
            assertThat(mapper.readTree(event.sourcePayload()))
                    .isEqualTo(frames.get(event.sourceFrameIndex()).path("events").get(event.sourceEventIndex()));
        }
        assertThat(extractor.extract("KR_8391829272", rawData)).isEqualTo(result);
    }

    private String timeline(String events) {
        return "{\"metadata\":{\"matchId\":\"KR_TEST\"},\"info\":{\"frames\":[{\"events\":["
                + events + "]}]}}";
    }
}
