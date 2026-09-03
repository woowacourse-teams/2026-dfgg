package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatchVersionTest {

    @Test
    @DisplayName("마이너 버전을 숫자로 비교한다 — 문자열 정렬이면 16.10이 16.9보다 앞서는 버그가 난다")
    void compareTo_WhenMinorVersionDigitCountDiffers_ComparesNumericallyNotLexicographically() {
        // given
        PatchVersion earlier = PatchVersion.of("16.9");
        PatchVersion later = PatchVersion.of("16.10");

        // when & then
        assertThat(earlier).isLessThan(later);
        assertThat("16.10".compareTo("16.9")).isNegative(); // 문자열 비교는 반대 결과임을 명시
    }

    @Test
    @DisplayName("메이저 버전이 다르면 메이저로 먼저 비교한다")
    void compareTo_WhenMajorVersionsDiffer_ComparesByMajorFirst() {
        // given
        PatchVersion lastSeason = PatchVersion.of("15.24");
        PatchVersion thisSeason = PatchVersion.of("16.1");

        // when & then
        assertThat(lastSeason).isLessThan(thisSeason);
    }

    @Test
    @DisplayName("실제 패치 목록을 정렬하면 수집 순서대로 나열된다")
    void sort_WhenRealPatchesGiven_OrdersChronologically() {
        // given
        List<PatchVersion> patches = new ArrayList<>(List.of(
                PatchVersion.of("16.17"), PatchVersion.of("16.9"),
                PatchVersion.of("15.24"), PatchVersion.of("16.10"), PatchVersion.of("16.2")
        ));

        // when
        Collections.sort(patches);

        // then
        assertThat(patches).extracting(PatchVersion::value)
                .containsExactly("15.24", "16.2", "16.9", "16.10", "16.17");
    }

    @Test
    @DisplayName("같은 패치는 동등하다")
    void equals_WhenSamePatchString_AreEqual() {
        assertThat(PatchVersion.of("16.15")).isEqualTo(PatchVersion.of("16.15"));
    }

    @Test
    @DisplayName("major.minor 형식이 아니면 거부한다")
    void of_WhenNotMajorMinorFormat_ThrowsException() {
        assertThatThrownBy(() -> PatchVersion.of("16")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatchVersion.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatchVersion.of("abc.def")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("major.minor 뒤에 빌드 번호가 붙어와도 major.minor만 취한다")
    void of_WhenFullGameVersionGiven_KeepsOnlyMajorMinor() {
        // given & when
        PatchVersion patch = PatchVersion.of("16.15.1.1");

        // then
        assertThat(patch.value()).isEqualTo("16.15");
    }
}
