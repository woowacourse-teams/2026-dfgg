package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.verify;

import dfgg.application.rune.RuneService;
import dfgg.application.spell.SummonerSpellService;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PickCatalogControllerTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private RuneService runeService;

    @MockitoBean
    private SummonerSpellService spellService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("룬 적재를 요청하면 204로 응답한다")
    void syncRunes_WhenRequested_Respond204() {
        given()
                .when().post("/admin/runes")
                .then().statusCode(204);

        verify(runeService).syncRunes();
    }

    @Test
    @DisplayName("소환사 주문 적재를 요청하면 204로 응답한다")
    void syncSpells_WhenRequested_Respond204() {
        given()
                .when().post("/admin/summoner-spells")
                .then().statusCode(204);

        verify(spellService).syncSpells();
    }
}
