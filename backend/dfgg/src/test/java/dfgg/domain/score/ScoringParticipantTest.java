package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dfgg.common.exception.InvalidDfScoreRequestException;
import dfgg.domain.champion.ChampionPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoringParticipantTest {

    @Test
    @DisplayName("오브젝트 피해가 포탑 피해 이상이면 만들어진다")
    void constructor_WhenObjectiveDamageCoversTurretDamage_Created() {
        // when & then
        assertThatCode(() -> new ScoringParticipant(100, ChampionPosition.TOP, true,
                3, 4, 5, 20000, 18000, 11000, 20, 3000, 3000))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("오브젝트 피해가 포탑 피해보다 작으면 예외가 발생한다 — 오브젝트 피해는 포탑 피해를 포함한다")
    void constructor_WhenObjectiveDamageIsLessThanTurretDamage_ThrowInvalidDfScoreRequestException() {
        // when & then
        assertThatThrownBy(() -> new ScoringParticipant(100, ChampionPosition.TOP, true,
                3, 4, 5, 20000, 18000, 11000, 20, 3000, 2999))
                .isInstanceOf(InvalidDfScoreRequestException.class)
                .hasMessageContaining("오브젝트 피해");
    }

    @Test
    @DisplayName("포지션이 없어도 만들어진다 — 칼바람처럼 포지션이 없는 모드가 있다")
    void constructor_WhenPositionIsMissing_Created() {
        // when & then
        assertThatCode(() -> new ScoringParticipant(100, null, true,
                3, 4, 5, 20000, 18000, 11000, 20, 3000, 5000))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("받은 피해는 생존 횟수(데스 + 1)로 나눈다 — 많이 죽으면서 맞은 피해를 탱킹으로 치지 않는다")
    void statOf_WhenDamageTakenPerLife_DivideByDeathsPlusOne() {
        // given
        ScoringParticipant participant = new ScoringParticipant(100, ChampionPosition.TOP, true,
                3, 4, 5, 20000, 18000, 11000, 20, 3000, 5000);

        // when
        double damageTakenPerLife = participant.statOf(ScoreFeature.DAMAGE_TAKEN_PER_LIFE);

        // then
        assertThat(damageTakenPerLife).isEqualTo(3600.0);
    }

    @Test
    @DisplayName("에픽 몬스터 피해는 오브젝트 피해에서 포탑 피해를 뺀 값이다")
    void statOf_WhenEpicMonsterDamage_SubtractTurretFromObjective() {
        // given
        ScoringParticipant participant = new ScoringParticipant(100, ChampionPosition.JUNGLE, true,
                3, 4, 5, 20000, 18000, 11000, 20, 3000, 25000);

        // when
        double epicMonsterDamage = participant.statOf(ScoreFeature.EPIC_MONSTER_DAMAGE);

        // then
        assertThat(epicMonsterDamage).isEqualTo(22000.0);
    }

    @Test
    @DisplayName("KDA는 (킬 + 어시스트) ÷ 데스다")
    void kda_WhenDied_DivideByDeaths() {
        // given
        ScoringParticipant participant = new ScoringParticipant(100, ChampionPosition.TOP, true,
                3, 4, 5, 20000, 18000, 11000, 20, 3000, 5000);

        // when
        double kda = participant.kda();

        // then
        assertThat(kda).isEqualTo(2.0);
    }

    @Test
    @DisplayName("데스가 0이면 KDA는 킬 + 어시스트다")
    void kda_WhenNeverDied_ReturnKillInvolvement() {
        // given
        ScoringParticipant participant = new ScoringParticipant(100, ChampionPosition.TOP, true,
                3, 0, 5, 20000, 18000, 11000, 20, 3000, 5000);

        // when
        double kda = participant.kda();

        // then
        assertThat(kda).isEqualTo(8.0);
    }

    @Test
    @DisplayName("허용치를 넘은 데스 수를 구한다")
    void excessDeaths_WhenDiedMoreThanAllowed_ReturnDifference() {
        // given
        ScoringParticipant participant = new ScoringParticipant(100, ChampionPosition.TOP, true,
                3, 10, 5, 20000, 18000, 11000, 20, 3000, 5000);

        // when
        double excess = participant.excessDeaths(8.55);

        // then
        assertThat(excess).isCloseTo(1.45, within(1e-9));
    }

    @Test
    @DisplayName("허용치 이하로 죽었으면 넘은 데스는 0이다")
    void excessDeaths_WhenDiedWithinAllowed_ReturnZero() {
        // given
        ScoringParticipant participant = new ScoringParticipant(100, ChampionPosition.TOP, true,
                3, 4, 5, 20000, 18000, 11000, 20, 3000, 5000);

        // when
        double excess = participant.excessDeaths(8.55);

        // then
        assertThat(excess).isZero();
    }
}
