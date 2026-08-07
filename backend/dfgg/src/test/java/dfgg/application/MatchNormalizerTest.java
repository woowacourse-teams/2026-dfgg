package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.NormalizedMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchNormalizerTest {

    private final MatchNormalizer normalizer = new MatchNormalizer(
            new ObjectMapper(),
            new CoreItemPurchaseOrderCalculator()
    );

    @Test
    void 매치_상세와_Timeline을_정규화한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1234567890",
                """
                        {
                          "info": {
                            "gameVersion": "16.15.1.1",
                            "queueId": 420,
                            "participants": [
                              {
                                "puuid": "blue-puuid",
                                "participantId": 1,
                                "championId": 266,
                                "teamId": 100,
                                "teamPosition": "TOP",
                                "item0": 3071,
                                "item1": 6610,
                                "item2": 0,
                                "win": true
                              }
                            ]
                          }
                        }
                        """,
                """
                        {
                          "metadata": {"participants": ["blue-puuid"]},
                          "info": {
                            "frames": [
                              {"events": [
                                {"timestamp": 100, "type": "ITEM_PURCHASED", "participantId": 1, "itemId": 3071},
                                {"timestamp": 200, "type": "ITEM_PURCHASED", "participantId": 1, "itemId": 6610}
                              ]}
                            ]
                          }
                        }
                        """,
                List.of(3071, 6610)
        );

        assertThat(normalized.matchId()).isEqualTo("KR_1234567890");
        assertThat(normalized.patch()).isEqualTo("16.15");
        assertThat(normalized.queueId()).isEqualTo(420);
        assertThat(normalized.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.puuid()).isEqualTo("blue-puuid");
            assertThat(participant.participantId()).isEqualTo(1);
            assertThat(participant.finalCoreItemIds()).containsExactly(3071, 6610);
            assertThat(participant.coreItemPurchaseOrder()).containsExactly(3071, 6610);
            assertThat(participant.coreItemPurchaseOrderComplete()).isTrue();
        });
    }

    @Test
    void participantId가_없으면_참가자_배열_순서로_보완한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"puuid-1","championId":1,"teamId":100,"win":true},
                          {"puuid":"puuid-2","championId":2,"teamId":200,"win":false}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["puuid-1","puuid-2"]},"info":{"frames":[]}}
                        """,
                List.of()
        );

        assertThat(normalized.participants())
                .extracting(participant -> participant.participantId())
                .containsExactly(1, 2);
    }

    @Test
    void participantId가_없으면_Timeline_metadata의_PUUID_순서를_우선한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"puuid-2","championId":2,"teamId":200,"item0":3071,"win":false},
                          {"puuid":"puuid-1","championId":1,"teamId":100,"item0":3071,"win":true}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["puuid-1","puuid-2"]},"info":{"frames":[
                          {"events":[
                            {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                            {"timestamp":200,"type":"ITEM_PURCHASED","participantId":2,"itemId":3071}
                          ]}
                        ]}}
                        """,
                List.of(3071)
        );

        assertThat(normalized.participants())
                .extracting(participant -> participant.participantId())
                .containsExactly(2, 1);
    }

    @Test
    void 패치_정보가_없으면_정규화하지_않는다() {
        assertThatThrownBy(() -> normalizer.normalize(
                "KR_1",
                "{\"info\":{\"queueId\":420,\"participants\":[]}}",
                "{\"metadata\":{\"participants\":[]},\"info\":{\"frames\":[]}}",
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("match participants must not be empty");
    }
}
