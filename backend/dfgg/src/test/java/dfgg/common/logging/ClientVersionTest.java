package dfgg.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClientVersionTest {

    @Test
    @DisplayName("헤더가 없으면 none으로 남긴다")
    void from_WhenHeaderAbsent_LogNone() {
        assertThat(ClientVersion.from(null).logValue()).isEqualTo("none");
        assertThat(ClientVersion.from("").logValue()).isEqualTo("none");
    }

    @Test
    @DisplayName("버전 형식이면 그 값을 그대로 남긴다")
    void from_WhenVersion_LogAsIs() {
        assertThat(ClientVersion.from("1.4.2").logValue()).isEqualTo("1.4.2");
        assertThat(ClientVersion.from("1.5.0-beta.1+build7").logValue()).isEqualTo("1.5.0-beta.1+build7");
    }

    @Test
    @DisplayName("허용하지 않는 문자나 32자를 넘는 값은 invalid로 남긴다")
    void from_WhenMalformed_LogInvalid() {
        assertThat(ClientVersion.from("1.4.2 <script>").logValue()).isEqualTo("invalid");
        assertThat(ClientVersion.from("1.4.2\nfake-line").logValue()).isEqualTo("invalid");
        assertThat(ClientVersion.from("1".repeat(33)).logValue()).isEqualTo("invalid");
    }
}
