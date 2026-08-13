package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.NormalizedMatch;
import java.util.List;
import java.util.Map;
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
    void BOTTOM_퀘스트_완료로_이동한_신발을_최종_빌드에_포함한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"bottom-puuid","participantId":1,"championId":222,"teamId":100,
                           "teamPosition":"BOTTOM","item0":3071,"item1":6610,"item2":3053,
                           "item3":6333,"item4":6676,"item5":3031,"roleBoundItem":3006,"win":true}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["bottom-puuid"]},"info":{"frames":[
                          {"events":[
                            {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3006},
                            {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                            {"timestamp":300,"type":"ITEM_PURCHASED","participantId":1,"itemId":6610},
                            {"timestamp":400,"type":"ITEM_PURCHASED","participantId":1,"itemId":3053},
                            {"timestamp":500,"type":"ITEM_PURCHASED","participantId":1,"itemId":6333},
                            {"timestamp":600,"type":"ITEM_PURCHASED","participantId":1,"itemId":6676},
                            {"timestamp":700,"type":"ITEM_PURCHASED","participantId":1,"itemId":3031}
                          ]}
                        ]}}
                        """,
                List.of(3071, 6610, 3053, 6333, 6676, 3031, 3006)
        );

        assertThat(normalized.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.finalCoreItemIds())
                    .containsExactly(3071, 6610, 3053, 6333, 6676, 3031, 3006);
            assertThat(participant.coreItemPurchaseOrder())
                    .containsExactly(3006, 3071, 6610, 3053, 6333, 6676, 3031);
            assertThat(participant.coreItemPurchaseOrderComplete()).isTrue();
        });
    }

    @Test
    void BOTTOM이_아닌_포지션의_roleBoundItem은_최종_빌드에서_제외한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"top-puuid","participantId":1,"championId":266,"teamId":100,
                           "teamPosition":"TOP","item0":3071,"roleBoundItem":3006,"win":true}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["top-puuid"]},"info":{"frames":[
                          {"events":[
                            {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                            {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":3006}
                          ]}
                        ]}}
                        """,
                List.of(3071, 3006)
        );

        assertThat(normalized.participants()).singleElement().satisfies(participant ->
                assertThat(participant.finalCoreItemIds()).containsExactly(3071)
        );
    }

    @Test
    void 모든_3티어_신발을_실제로_구매한_신발로_보정한다() {
        Map<Integer, Integer> expectedPurchasedBoots = Map.of(
                3168, 3008,
                3170, 3009,
                3171, 3158,
                3172, 3006,
                3173, 3111,
                3174, 3047,
                3175, 3020,
                3176, 3010
        );

        expectedPurchasedBoots.forEach((tierThreeBoot, purchasedBoot) -> {
            NormalizedMatch normalized = normalizer.normalize(
                    "KR_" + tierThreeBoot,
                    """
                            {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                              {"puuid":"puuid-1","participantId":1,"championId":1,"teamId":100,
                               "teamPosition":"TOP","item0":%d,"win":true}
                            ]}}
                            """.formatted(tierThreeBoot),
                    """
                            {"metadata":{"participants":["puuid-1"]},"info":{"frames":[
                              {"events":[
                                {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":%d}
                              ]}
                            ]}}
                            """.formatted(purchasedBoot),
                    List.of(purchasedBoot)
            );

            assertThat(normalized.participants()).singleElement().satisfies(participant -> {
                assertThat(participant.finalCoreItemIds())
                        .as("tier-three boot %s", tierThreeBoot)
                        .containsExactly(purchasedBoot);
                assertThat(participant.coreItemPurchaseOrder())
                        .as("tier-three boot %s", tierThreeBoot)
                        .containsExactly(purchasedBoot);
                assertThat(participant.coreItemPurchaseOrderComplete()).isTrue();
            });
        });
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
