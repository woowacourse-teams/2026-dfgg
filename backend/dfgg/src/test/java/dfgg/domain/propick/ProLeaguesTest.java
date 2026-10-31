package dfgg.domain.propick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProLeaguesTest {

    private final ProLeagues leagues = new ProLeagues(List.of(
            "2026 Season World Championship/", "2026 Mid-Season Invitational", "2026 First Stand",
            "LCK/", "LPL/", "LEC/", "LCS/", "LCP/", "CBLOL/"));

    @ParameterizedTest
    @ValueSource(strings = {
            "2026 Season World Championship/Main Event",
            "2026 Season World Championship/Play-In",
            "2026 Mid-Season Invitational",
            "LCK/2026 Season/Playoffs",
            "CBLOL/2026 Season/Split 2"})
    @DisplayName("설정한 접두사로 시작하는 대회는 포함한다")
    void includes_WhenOverviewPageStartsWithPrefix_ReturnTrue(String overviewPage) {
        assertThat(leagues.includes(overviewPage)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LCK CL/2026 Season/Rounds 1-2",
            "KeSPA Cup/2026",
            "Esports World Cup 2026",
            "LTA North/2026 Season/Split 1",
            "lck/2026 Season/Playoffs",
            ""})
    @DisplayName("하부 리그·KeSPA Cup·EWC처럼 접두사가 다른 대회는 뺀다 (대소문자도 구분)")
    void includes_WhenOverviewPageOutsidePrefixes_ReturnFalse(String overviewPage) {
        assertThat(leagues.includes(overviewPage)).isFalse();
    }

    @Test
    @DisplayName("접두사 목록을 설정 순서대로 돌려준다")
    void prefixes_WhenConfigured_KeepOrder() {
        assertThat(leagues.prefixes()).startsWith("2026 Season World Championship/", "2026 Mid-Season Invitational");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LCK/' OR '1'='1", "LCK/%", "LCK_", "  "})
    @DisplayName("Cargo 조건에 그대로 들어가므로 따옴표·와일드카드·빈 접두사는 거부한다")
    void new_WhenPrefixUnsafe_Throw(String prefix) {
        assertThatThrownBy(() -> new ProLeagues(List.of(prefix)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("리그가 하나도 없으면 거부한다")
    void new_WhenEmpty_Throw() {
        assertThatThrownBy(() -> new ProLeagues(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
