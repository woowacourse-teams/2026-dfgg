package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeagueOfLegendsVersionPropertiesTest {

    @Test
    @DisplayName("설정한 패치 버전을 앞뒤 공백 없이 준다")
    void version_WhenConfigured_ReturnStrippedVersion() {
        // when
        LeagueOfLegendsVersionProperties properties = new LeagueOfLegendsVersionProperties(" 16.18 ");

        // then
        assertThat(properties.version()).isEqualTo("16.18");
    }

    @Test
    @DisplayName("버전이 비면 기동을 거부하고 어느 설정인지 알려준다 — 이미지 URL의 버전 폴더를 정할 수 없다")
    void constructor_WhenVersionBlank_ThrowNamingTheProperty() {
        // when & then
        assertThatThrownBy(() -> new LeagueOfLegendsVersionProperties(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("riot.version");
    }
}
