package dfgg.infrastructure.external.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.rune.RunePage;
import dfgg.domain.rune.StatShards;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchPerksTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Riot 원본 perks를 선택 순서 그대로 룬 페이지로 바꾼다")
    void toRunePage_ConvertsRiotPerksInSelectionOrder() throws Exception {
        // given
        MatchPerks perks = read("""
                {
                  "statPerks": { "defense": 5001, "flex": 5008, "offense": 5005 },
                  "styles": [
                    {
                      "description": "primaryStyle",
                      "style": 8400,
                      "selections": [
                        { "perk": 8437, "var1": 1, "var2": 0, "var3": 0 },
                        { "perk": 8446, "var1": 0, "var2": 0, "var3": 0 },
                        { "perk": 8473, "var1": 0, "var2": 0, "var3": 0 },
                        { "perk": 8451, "var1": 0, "var2": 0, "var3": 0 }
                      ]
                    },
                    {
                      "description": "subStyle",
                      "style": 8300,
                      "selections": [
                        { "perk": 8304, "var1": 0, "var2": 0, "var3": 0 },
                        { "perk": 8345, "var1": 0, "var2": 0, "var3": 0 }
                      ]
                    }
                  ]
                }
                """);

        // when
        RunePage runePage = perks.toRunePage();

        // then
        assertThat(runePage).isEqualTo(new RunePage(8400, List.of(8437, 8446, 8473, 8451),
                8300, List.of(8304, 8345), new StatShards(5005, 5008, 5001)));
    }

    @Test
    @DisplayName("배열 순서가 아니라 description으로 주 계열과 보조 계열을 구분한다")
    void toRunePage_IdentifiesStylesByDescription() throws Exception {
        // given
        MatchPerks perks = read("""
                {
                  "statPerks": { "defense": 5001, "flex": 5008, "offense": 5005 },
                  "styles": [
                    { "description": "subStyle", "style": 8300,
                      "selections": [ { "perk": 8304 }, { "perk": 8345 } ] },
                    { "description": "primaryStyle", "style": 8400,
                      "selections": [ { "perk": 8437 }, { "perk": 8446 }, { "perk": 8473 }, { "perk": 8451 } ] }
                  ]
                }
                """);

        // when & then
        assertThat(perks.toRunePage().keystoneId()).isEqualTo(8437);
        assertThat(perks.toRunePage().subStyleId()).isEqualTo(8300);
    }

    @Test
    @DisplayName("주 계열이 없으면 룬 페이지를 만들 수 없다")
    void toRunePage_WhenPrimaryStyleIsMissing_Throws() throws Exception {
        // given
        MatchPerks perks = read("""
                {
                  "statPerks": { "defense": 5001, "flex": 5008, "offense": 5005 },
                  "styles": [
                    { "description": "subStyle", "style": 8300,
                      "selections": [ { "perk": 8304 }, { "perk": 8345 } ] }
                  ]
                }
                """);

        // when & then
        assertThatThrownBy(perks::toRunePage)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primaryStyle");
    }

    @Test
    @DisplayName("파편이 없으면 룬 페이지를 만들 수 없다")
    void toRunePage_WhenStatPerksAreMissing_Throws() throws Exception {
        // given
        MatchPerks perks = read("""
                {
                  "styles": [
                    { "description": "primaryStyle", "style": 8400,
                      "selections": [ { "perk": 8437 }, { "perk": 8446 }, { "perk": 8473 }, { "perk": 8451 } ] },
                    { "description": "subStyle", "style": 8300,
                      "selections": [ { "perk": 8304 }, { "perk": 8345 } ] }
                  ]
                }
                """);

        // when & then
        assertThatThrownBy(perks::toRunePage)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파편");
    }

    private MatchPerks read(String json) throws Exception {
        return objectMapper.readValue(json, MatchPerks.class);
    }
}
