package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.common.exception.AugmentDataRejectedException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AugmentCatalogTest {

    @Test
    @DisplayName("증강이 카탈로그에 있는지 답한다")
    void contains_AnswersMembership() {
        // when
        AugmentCatalog catalog = AugmentCatalog.of(List.of(augment(1373, "축소 엔진", "Shrink Engine")));

        // then
        assertThat(catalog.contains(1373)).isTrue();
        assertThat(catalog.contains(9999)).isFalse();
    }

    @Test
    @DisplayName("같은 패치에 이름이 같은 증강이 있으면 거부한다")
    void of_WhenNameDuplicated_Rejects() {
        // given
        List<Augment> augments = List.of(augment(1, "축소 엔진", "Shrink Engine"), augment(2, "축소 엔진", "Other"));

        // when & then
        assertThatThrownBy(() -> AugmentCatalog.of(augments)).isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("이름이 같은 증강이 떨어져 있어도 거부한다")
    void of_WhenDuplicateNotAdjacent_Rejects() {
        // given
        List<Augment> augments = List.of(augment(1, "축소 엔진", "Shrink Engine"), augment(2, "되풀이", "Recursion"),
                augment(3, "축소 엔진", "Other"));

        // when & then
        assertThatThrownBy(() -> AugmentCatalog.of(augments)).isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("대소문자만 다른 이름도 같은 이름이다")
    void of_WhenNameDiffersOnlyByCase_Rejects() {
        // given
        List<Augment> augments = List.of(augment(1, "가", "Shrink Engine"), augment(2, "나", "shrink engine"));

        // when & then
        assertThatThrownBy(() -> AugmentCatalog.of(augments)).isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("증강이 하나도 없으면 거부한다")
    void of_WhenEmpty_Rejects() {
        // when & then
        assertThatThrownBy(() -> AugmentCatalog.of(List.of())).isInstanceOf(AugmentDataRejectedException.class);
    }

    private Augment augment(int augmentId, String koreanName, String englishName) {
        return new Augment("16.21", augmentId, Map.of("ko-KR", koreanName, "en-US", englishName), AugmentRarity.GOLD);
    }
}
