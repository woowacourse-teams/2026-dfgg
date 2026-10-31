package dfgg.domain.score.summonersrift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.score.DfScore;
import dfgg.domain.score.ScoringParticipant;
import dfgg.domain.score.ScoringParticipants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PositionScoreProfileTest {

    private static final ScoringParticipant BLUE_TOP = new ScoringParticipant(100, ChampionPosition.TOP, true,
            5, 3, 4, 25000, 30000, 13000, 18, 6000, 7000);
    private static final ScoringParticipant BLUE_JUNGLE = new ScoringParticipant(100, ChampionPosition.JUNGLE, true,
            6, 4, 10, 18000, 35000, 12000, 40, 1000, 25000);
    private static final ScoringParticipant BLUE_MID = new ScoringParticipant(100, ChampionPosition.MID, true,
            7, 2, 6, 30000, 15000, 13500, 22, 4000, 5000);
    private static final ScoringParticipant BLUE_BOTTOM = new ScoringParticipant(100, ChampionPosition.BOTTOM, true,
            8, 3, 5, 32000, 14000, 14500, 15, 5000, 9000);
    private static final ScoringParticipant BLUE_SUPPORT = new ScoringParticipant(100, ChampionPosition.SUPPORT, true,
            1, 4, 18, 9000, 16000, 8500, 80, 500, 800);
    private static final ScoringParticipant RED_TOP = new ScoringParticipant(200, ChampionPosition.TOP, false,
            3, 6, 3, 20000, 32000, 11000, 15, 3000, 3500);
    private static final ScoringParticipant RED_JUNGLE = new ScoringParticipant(200, ChampionPosition.JUNGLE, false,
            4, 7, 6, 15000, 33000, 10000, 35, 0, 18000);
    private static final ScoringParticipant RED_MID = new ScoringParticipant(200, ChampionPosition.MID, false,
            5, 6, 4, 24000, 18000, 11500, 20, 2000, 2500);
    private static final ScoringParticipant RED_BOTTOM = new ScoringParticipant(200, ChampionPosition.BOTTOM, false,
            4, 7, 3, 22000, 15000, 11000, 12, 1500, 4000);
    private static final ScoringParticipant RED_SUPPORT = new ScoringParticipant(200, ChampionPosition.SUPPORT, false,
            0, 6, 10, 7000, 17000, 7000, 70, 0, 300);

    private static final ScoringParticipants MATCH = new ScoringParticipants(List.of(
            BLUE_TOP, BLUE_JUNGLE, BLUE_MID, BLUE_BOTTOM, BLUE_SUPPORT,
            RED_TOP, RED_JUNGLE, RED_MID, RED_BOTTOM, RED_SUPPORT));

    @ParameterizedTest(name = "{0}")
    @EnumSource(PositionScoreProfile.class)
    @DisplayName("포지션마다 가중치의 합은 1이다")
    void weights_WhenSummed_EqualOne(PositionScoreProfile profile) {
        // when
        double sum = profile.weightSum();

        // then
        assertThat(sum).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("포지션 상수는 같은 이름의 포지션에서 찾는다")
    void of_WhenPositionGiven_ReturnSameNamedProfile() {
        // when
        PositionScoreProfile profile = PositionScoreProfile.of(ChampionPosition.SUPPORT);

        // then
        assertThat(profile).isEqualTo(PositionScoreProfile.SUPPORT);
    }

    @Test
    @DisplayName("개인 지표 A는 참조 구현과 같다")
    void individualPerformance_WhenReferenceMatch_EqualReferenceValues() {
        // when & then
        assertThat(PositionScoreProfile.TOP.individualPerformance(BLUE_TOP, MATCH)).isCloseTo(0.629710, within(1e-6));
        assertThat(PositionScoreProfile.JUNGLE.individualPerformance(BLUE_JUNGLE, MATCH)).isCloseTo(0.636835, within(1e-6));
        assertThat(PositionScoreProfile.SUPPORT.individualPerformance(BLUE_SUPPORT, MATCH)).isCloseTo(0.526231, within(1e-6));
        assertThat(PositionScoreProfile.MID.individualPerformance(RED_MID, MATCH)).isCloseTo(0.513898, within(1e-6));
        assertThat(PositionScoreProfile.BOTTOM.individualPerformance(RED_BOTTOM, MATCH)).isCloseTo(0.438499, within(1e-6));
    }

    @Test
    @DisplayName("서포터는 포탑·에픽 몬스터 피해를 쓰지 않는다 — 값이 달라도 개인 지표가 같다")
    void individualPerformance_WhenSupportObjectiveDamageChanges_StaySame() {
        // given
        ScoringParticipant pushingSupport = new ScoringParticipant(100, ChampionPosition.SUPPORT, true,
                1, 4, 18, 9000, 16000, 8500, 80, 500, 30000);

        // when
        double original = PositionScoreProfile.SUPPORT.individualPerformance(BLUE_SUPPORT, MATCH);
        double pushing = PositionScoreProfile.SUPPORT.individualPerformance(pushingSupport, MATCH);

        // then
        assertThat(pushing).isEqualTo(original);
    }

    @Test
    @DisplayName("기본점수가 포지션 평균이면 50점이다")
    void standardize_WhenBaseScoreEqualsPositionMean_ReturnFifty() {
        // when
        DfScore score = PositionScoreProfile.TOP.standardize(40.5);

        // then
        assertThat(score.value()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("기본점수가 포지션 평균보다 표준편차 하나 높으면 70점이다")
    void standardize_WhenOneStandardDeviationAbove_ReturnSeventy() {
        // when
        DfScore score = PositionScoreProfile.TOP.standardize(40.5 + 19.4);

        // then
        assertThat(score.value()).isEqualTo(70.0);
    }
}
