package dfgg.infrastructure.external.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GeminiTestIsolationTest {

    @Autowired
    private GeminiProperties properties;

    @Test
    @DisplayName("테스트 프로필에서는 .env에 키가 있어도 Gemini 자격 증명이 비어 있다")
    void hasCredentials_WhenTestProfile_ReturnFalse() {
        // when
        boolean hasCredentials = properties.hasCredentials();

        // then
        assertThat(hasCredentials).isFalse();
    }
}
