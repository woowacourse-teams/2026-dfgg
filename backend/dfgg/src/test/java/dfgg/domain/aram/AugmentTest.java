package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.language.Language;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AugmentTest {

    @Test
    @DisplayName("요청한 언어의 이름을 그대로 준다")
    void exactNameIn_WhenNameExists_ReturnsName() {
        // given
        Augment augment = new Augment("16.21", 1373,
                Map.of("ko-KR", "축소 엔진", "en-US", "Shrink Engine"), AugmentRarity.GOLD);

        // when
        Optional<String> name = augment.exactNameIn(Language.EN_US);

        // then
        assertThat(name).contains("Shrink Engine");
    }

    @Test
    @DisplayName("요청한 언어의 이름이 없으면 한글 이름으로 대신하지 않는다")
    void exactNameIn_WhenNameMissing_ReturnsEmpty() {
        // given
        Augment augment = new Augment("16.21", 1373, Map.of("ko-KR", "축소 엔진"), AugmentRarity.GOLD);

        // when
        Optional<String> name = augment.exactNameIn(Language.EN_US);

        // then
        assertThat(name).isEmpty();
    }
}
