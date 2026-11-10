package dfgg.application.patch;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.PatchVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicePatchTest {

    @Test
    @DisplayName("더 높은 패치로는 바뀐다")
    void advanceTo_WhenNewerPatch_ChangesCurrent() {
        // given
        ServicePatch servicePatch = new ServicePatch(PatchVersion.of("16.21"));

        // when
        boolean changed = servicePatch.advanceTo(PatchVersion.of("16.22"));

        // then
        assertThat(changed).isTrue();
        assertThat(servicePatch.value()).isEqualTo("16.22");
    }

    @Test
    @DisplayName("같거나 낮은 패치로는 되돌아가지 않는다")
    void advanceTo_WhenSameOrOlderPatch_KeepsCurrent() {
        // given
        ServicePatch servicePatch = new ServicePatch(PatchVersion.of("16.22"));

        // when
        boolean same = servicePatch.advanceTo(PatchVersion.of("16.22"));
        boolean older = servicePatch.advanceTo(PatchVersion.of("16.9"));

        // then
        assertThat(same).isFalse();
        assertThat(older).isFalse();
        assertThat(servicePatch.value()).isEqualTo("16.22");
    }

    @Test
    @DisplayName("문자열이 아니라 패치 순서로 비교한다")
    void advanceTo_WhenMinorHasMoreDigits_ComparesNumerically() {
        // given
        ServicePatch servicePatch = new ServicePatch(PatchVersion.of("16.9"));

        // when
        boolean changed = servicePatch.advanceTo(PatchVersion.of("16.10"));

        // then
        assertThat(changed).isTrue();
        assertThat(servicePatch.value()).isEqualTo("16.10");
    }
}
