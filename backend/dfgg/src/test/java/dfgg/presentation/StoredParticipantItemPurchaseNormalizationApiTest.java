package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.match.GoldRangeStatus;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemPurchase;
import dfgg.domain.match.ParticipantItemPurchaseRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import io.restassured.RestAssured;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StoredParticipantItemPurchaseNormalizationApiTest {

    private static final String MATCH_ID = "KR_API_PURCHASE_SMOKE";

    @LocalServerPort
    private int port;

    @Autowired
    private RawMatchRepository rawMatchRepository;

    @Autowired
    private RawMatchTimelineRepository timelineRepository;

    @Autowired
    private ParticipantItemPurchaseRepository purchaseRepository;

    @MockitoBean
    private DataDragonClient dataDragonClient;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        rawMatchRepository.save(new RawMatch(MATCH_ID,
                "{\"info\":{\"gameVersion\":\"16.18.1.123\",\"participants\":["
                        + "{\"participantId\":1,\"teamPosition\":\"MIDDLE\"}]}}"));
        timelineRepository.save(new RawMatchTimeline(MATCH_ID, timeline()));
        when(dataDragonClient.resolveItemDataVersionForPatch("16.18")).thenReturn("16.18.1");
        when(dataDragonClient.getItems("16.18.1")).thenReturn(catalog());
    }

    @AfterEach
    void cleanUp() {
        purchaseRepository.deleteAll(purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc(MATCH_ID));
        timelineRepository.deleteById(MATCH_ID);
        rawMatchRepository.deleteById(MATCH_ID);
    }

    @Test
    void 관리자_API로_전체_구매를_즉시_정규화하고_재실행해도_중복되지_않는다() {
        for (int attempt = 0; attempt < 2; attempt++) {
            var response = given()
                    .when().post("/admin/riot/matches/purchases/normalize")
                    .then().statusCode(200).extract().response();
            assertThat(response.jsonPath().getInt("processed")).isEqualTo(1);
            assertThat(response.jsonPath().getInt("succeeded")).isEqualTo(1);
            assertThat(response.jsonPath().getInt("savedPurchases")).isEqualTo(1);
        }

        List<ParticipantItemPurchase> purchases =
                purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc(MATCH_ID);
        assertThat(purchases).hasSize(1);
        ParticipantItemPurchase purchase = purchases.getFirst();
        assertThat(purchase.getItemId()).isEqualTo(1036);
        assertThat(purchase.getPosition()).isEqualTo("MIDDLE");
        assertThat(purchase.getPurchaseType()).isEqualTo(ItemPurchaseType.COMPONENT);
        assertThat(purchase.getItemCost()).isEqualTo(350);
        assertThat(purchase.getGoldLower()).isEqualTo(500);
        assertThat(purchase.getGoldUpper()).isEqualTo(1_000);
        assertThat(purchase.getGoldRangeStatus()).isEqualTo(GoldRangeStatus.TOTAL_GOLD_REFINED);
        verify(dataDragonClient, org.mockito.Mockito.times(2)).resolveItemDataVersionForPatch("16.18");
        verify(dataDragonClient, org.mockito.Mockito.times(2)).getItems("16.18.1");
    }

    private ItemResponse catalog() {
        ItemData longSword = new ItemData("Long Sword", List.of(), List.of("1053"), List.of("Damage"),
                Map.of("11", true), false, 1, new ItemData.Gold(350, 350, true), null, true, false);
        ItemData scepter = new ItemData("Vampiric Scepter", List.of("1036"), List.of(), List.of("Damage"),
                Map.of("11", true), false, 2, new ItemData.Gold(550, 900, true), null, true, false);
        return new ItemResponse("16.18", "16.18.1", Map.of("1036", longSword, "1053", scepter));
    }

    private String timeline() {
        return "{\"metadata\":{\"matchId\":\"" + MATCH_ID + "\"},\"info\":{\"frames\":["
                + "{\"participantFrames\":{\"1\":{\"currentGold\":500,\"totalGold\":1000}},\"events\":[]},"
                + "{\"participantFrames\":{\"1\":{\"currentGold\":900,\"totalGold\":1500}},\"events\":["
                + "{\"type\":\"ITEM_PURCHASED\",\"participantId\":1,\"itemId\":1036,\"timestamp\":100}"
                + "]}]}}";
    }
}
