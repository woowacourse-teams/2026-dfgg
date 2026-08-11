package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RiotApiKeyRotatorTest {

    @Test
    void currentKey_초기_상태에서는_첫_번째_키를_반환한다() {
        RiotApiKeyRotator rotator = new RiotApiKeyRotator(List.of("KEY_A", "KEY_B"));

        assertThat(rotator.currentKey()).isEqualTo("KEY_A");
    }

    @Test
    void rotateToNextAvailable_현재_키를_차단하면_차단되지_않은_다음_키로_회전한다() {
        RiotApiKeyRotator rotator = new RiotApiKeyRotator(List.of("KEY_A", "KEY_B"));
        rotator.blockCurrentUntil(1_000L);

        boolean rotated = rotator.rotateToNextAvailable(0L);

        assertThat(rotated).isTrue();
        assertThat(rotator.currentKey()).isEqualTo("KEY_B");
    }

    @Test
    void rotateToNextAvailable_모든_키가_차단되어_있으면_회전하지_못하고_현재_키를_유지한다() {
        RiotApiKeyRotator rotator = new RiotApiKeyRotator(List.of("KEY_A", "KEY_B"));
        rotator.blockCurrentUntil(1_000L);
        rotator.rotateToNextAvailable(0L);
        rotator.blockCurrentUntil(2_000L);

        boolean rotated = rotator.rotateToNextAvailable(0L);

        assertThat(rotated).isFalse();
        assertThat(rotator.currentKey()).isEqualTo("KEY_B");
    }

    @Test
    void rotateToNextAvailable_키가_하나뿐이면_항상_회전에_실패한다() {
        RiotApiKeyRotator rotator = new RiotApiKeyRotator(List.of("ONLY_KEY"));
        rotator.blockCurrentUntil(1_000L);

        boolean rotated = rotator.rotateToNextAvailable(0L);

        assertThat(rotated).isFalse();
        assertThat(rotator.currentKey()).isEqualTo("ONLY_KEY");
    }

    @Test
    void millisUntilEarliestAvailable_모든_키가_차단되면_가장_빨리_풀리는_키로_전환하고_남은_대기시간을_반환한다() {
        RiotApiKeyRotator rotator = new RiotApiKeyRotator(List.of("KEY_A", "KEY_B"));
        rotator.blockCurrentUntil(5_000L);
        rotator.rotateToNextAvailable(0L);
        rotator.blockCurrentUntil(2_000L);

        long waitMillis = rotator.millisUntilEarliestAvailable(0L);

        assertThat(waitMillis).isEqualTo(2_000L);
        assertThat(rotator.currentKey()).isEqualTo("KEY_B");
    }

    @Test
    void millisUntilEarliestAvailable_이미_사용_가능한_키가_있으면_대기시간_0을_반환한다() {
        RiotApiKeyRotator rotator = new RiotApiKeyRotator(List.of("KEY_A", "KEY_B"));
        rotator.blockCurrentUntil(5_000L);

        long waitMillis = rotator.millisUntilEarliestAvailable(6_000L);

        assertThat(waitMillis).isEqualTo(0L);
    }

    @Test
    void 빈_키_목록으로_생성할_수_없다() {
        assertThatThrownBy(() -> new RiotApiKeyRotator(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKeys must not be empty");
    }
}
