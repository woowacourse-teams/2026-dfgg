package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.common.exception.AugmentDataRejectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AugmentCatalogTest {

    @Test
    @DisplayName("증강이 카탈로그에 있는지 답한다")
    void contains_AnswersMembership() {
        // when
        AugmentCatalog catalog = AugmentCatalog.of(augments(3), 3);

        // then
        assertThat(catalog.contains(1)).isTrue();
        assertThat(catalog.contains(9999)).isFalse();
    }

    @Test
    @DisplayName("같은 패치에 이름이 같은 증강이 있으면 거부한다")
    void of_WhenNameDuplicated_Rejects() {
        // given
        List<Augment> augments = new ArrayList<>(augments(3));
        augments.add(augment(4, "증강1", "Other", AugmentRarity.GOLD));

        // when & then
        assertRejected(augments, 4, "이름");
    }

    @Test
    @DisplayName("이름이 같은 증강이 떨어져 있어도 거부한다")
    void of_WhenDuplicateNotAdjacent_Rejects() {
        // given
        List<Augment> augments = new ArrayList<>(augments(5));
        augments.add(augment(6, "증강1", "Other", AugmentRarity.GOLD));

        // when & then
        assertRejected(augments, 6, "이름");
    }

    @Test
    @DisplayName("대소문자만 다른 이름도 같은 이름이다")
    void of_WhenNameDiffersOnlyByCase_Rejects() {
        // given
        List<Augment> augments = new ArrayList<>(augments(3));
        augments.add(augment(4, "다른 이름", "AUGMENT1", AugmentRarity.GOLD));

        // when & then
        assertRejected(augments, 4, "이름");
    }

    @Test
    @DisplayName("증강이 하나도 없으면 거부한다")
    void of_WhenEmpty_Rejects() {
        // when & then
        assertRejected(List.of(), 0, "연결된 증강");
    }

    @Test
    @DisplayName("연결률이 95%면 받는다")
    void of_WhenCoverageAtLimit_Accepts() {
        // when
        AugmentCatalog catalog = AugmentCatalog.of(augments(19), 20);

        // then
        assertThat(catalog.augments()).hasSize(19);
    }

    @Test
    @DisplayName("연결률이 95% 미만이면 거부한다")
    void of_WhenCoverageBelowLimit_Rejects() {
        // when & then
        assertRejected(augments(18), 19, "연결률");
    }

    @Test
    @DisplayName("없는 등급이 있으면 거부한다")
    void of_WhenRarityMissing_Rejects() {
        // given
        List<Augment> withoutPrismatic = List.of(
                augment(1, "가", "A", AugmentRarity.SILVER),
                augment(2, "나", "B", AugmentRarity.GOLD));

        // when & then
        assertRejected(withoutPrismatic, 2, "등급");
    }

    @Test
    @DisplayName("증강을 빼면 남은 증강으로 카탈로그를 만든다")
    void excluding_RemovesAugments() {
        // given
        AugmentCatalog catalog = AugmentCatalog.of(augments(21), 21);

        // when
        AugmentCatalog remaining = catalog.excluding(Set.of(1));

        // then
        assertThat(remaining.contains(1)).isFalse();
        assertThat(remaining.augments()).hasSize(20);
    }

    @Test
    @DisplayName("빼고 난 뒤의 연결률도 하한을 지켜야 한다")
    void excluding_WhenCoverageFallsBelowLimit_Rejects() {
        // given
        AugmentCatalog catalog = AugmentCatalog.of(augments(20), 20);

        // when & then
        assertThatThrownBy(() -> catalog.excluding(Set.of(1, 2)))
                .isInstanceOf(AugmentDataRejectedException.class)
                .hasMessageContaining("연결률");
    }

    private void assertRejected(List<Augment> augments, int expectedCount, String reason) {
        assertThatThrownBy(() -> AugmentCatalog.of(augments, expectedCount))
                .isInstanceOf(AugmentDataRejectedException.class)
                .hasMessageContaining(reason);
    }

    private List<Augment> augments(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(id -> augment(id, "증강" + id, "Augment" + id, AugmentRarity.values()[id % 3]))
                .toList();
    }

    private Augment augment(int augmentId, String koreanName, String englishName, AugmentRarity rarity) {
        return new Augment("16.21", augmentId, Map.of("ko-KR", koreanName, "en-US", englishName), rarity);
    }
}
