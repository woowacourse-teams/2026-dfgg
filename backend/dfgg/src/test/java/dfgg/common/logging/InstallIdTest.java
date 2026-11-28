package dfgg.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstallIdTest {

    @Test
    @DisplayName("헤더가 없으면 none으로 남긴다")
    void from_WhenHeaderAbsent_LogNone() {
        assertThat(InstallId.from(null).logValue()).isEqualTo("none");
        assertThat(InstallId.from(" ").logValue()).isEqualTo("none");
    }

    @Test
    @DisplayName("UUID 형식이면 그 값을 그대로 남긴다")
    void from_WhenUuid_LogAsIs() {
        // when
        InstallId installId = InstallId.from("3f2a9c1e-7b4d-4e2a-9c1e-5d6f7a8b9c0d");

        // then
        assertThat(installId.logValue()).isEqualTo("3f2a9c1e-7b4d-4e2a-9c1e-5d6f7a8b9c0d");
    }

    @Test
    @DisplayName("UUID 형식이 아니면 원문 대신 invalid로 남긴다")
    void from_WhenNotUuid_LogInvalid() {
        assertThat(InstallId.from("not-a-uuid").logValue()).isEqualTo("invalid");
    }

    @Test
    @DisplayName("줄바꿈을 섞은 값은 로그 위조를 막기 위해 invalid로 남긴다")
    void from_WhenContainsNewline_LogInvalid() {
        // when
        InstallId installId = InstallId.from("3f2a9c1e-7b4d-4e2a-9c1e-5d6f7a8b9c0d\nfake-line");

        // then
        assertThat(installId.logValue()).isEqualTo("invalid");
    }
}
