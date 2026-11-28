package dfgg.domain.propick;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProPickTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026 Season World Championship/Main Event_Round 1_1_2|2",
            "2026 Season World Championship/Play-In_Round 2_2_3|3",
            "LCK/2026 Season/Playoffs_Finals_1_10|10"})
    @DisplayName("세트 번호는 Leaguepedia GameId의 마지막 숫자다 — 대회 페이지_탭_매치 번호_세트 번호")
    void getSetNumber_WhenGameIdEndsWithNumber_ReturnLastNumber(String gameId, int setNumber) {
        // given
        ProPick pick = new ProPick(null, null, gameId);

        // when & then
        assertThat(pick.getSetNumber()).isEqualTo(setNumber);
    }

    @ParameterizedTest
    @ValueSource(strings = {"w1", "2026 Season/Main Event_Round 1_1_", "2026 Season/Main Event_Round 1_1_A"})
    @DisplayName("GameId 끝이 숫자가 아니면 세트 번호는 비어 있다")
    void getSetNumber_WhenGameIdHasNoTrailingNumber_ReturnNull(String gameId) {
        // given
        ProPick pick = new ProPick(null, null, gameId);

        // when & then
        assertThat(pick.getSetNumber()).isNull();
    }
}
