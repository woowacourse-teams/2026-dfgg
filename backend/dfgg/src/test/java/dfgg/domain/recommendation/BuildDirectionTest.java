package dfgg.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.champion.ChampionTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildDirectionTest {

    @Test
    @DisplayName("태그와 빌드 방향 코드를 보관한다")
    void createsBuildDirection() {
        // given
        ChampionTag championTag = ChampionTag.TANK;

        // when
        BuildDirection direction = new BuildDirection(championTag, "PHYSICAL_DAMAGE");

        // then
        assertThat(direction.championTag()).isEqualTo(championTag);
        assertThat(direction.code()).isEqualTo("PHYSICAL_DAMAGE");
    }

    @Test
    @DisplayName("빌드 방향 코드의 앞뒤 공백을 제거한다")
    void createsBuildDirection_TrimsCode() {
        // given
        String code = "  PHYSICAL_DAMAGE  ";

        // when
        BuildDirection direction = new BuildDirection(ChampionTag.TANK, code);

        // then
        assertThat(direction.code()).isEqualTo("PHYSICAL_DAMAGE");
    }

    @Test
    @DisplayName("빌드 방향 코드가 비어 있으면 생성할 수 없다")
    void createsBuildDirection_WhenCodeIsBlank_ThrowsException() {
        // given
        String blankCode = " ";

        // when & then
        assertThatThrownBy(() -> new BuildDirection(ChampionTag.TANK, blankCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("빌드 방향 코드는 비어 있을 수 없습니다.");
    }
}
