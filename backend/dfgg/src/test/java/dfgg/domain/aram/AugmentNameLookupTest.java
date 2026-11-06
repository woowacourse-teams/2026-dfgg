package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AugmentNameLookupTest {

    private static final List<Augment> CATALOG = List.of(
            new Augment("16.21", 1373, Map.of("ko-KR", "축소 엔진", "en-US", "Shrink Engine"), AugmentRarity.GOLD),
            new Augment("16.21", 1402, Map.of("ko-KR", "되풀이", "en-US", "Recursion"), AugmentRarity.PRISMATIC));

    @Test
    @DisplayName("한글 이름으로 증강을 찾는다")
    void of_FindsByKoreanName() {
        // when
        AugmentNameLookup lookup = AugmentNameLookup.of(CATALOG, List.of("되풀이"));

        // then
        assertThat(lookup.augments()).extracting(Augment::augmentId).containsExactly(1402);
    }

    @Test
    @DisplayName("영어 이름으로 증강을 찾는다")
    void of_FindsByEnglishName() {
        // when
        AugmentNameLookup lookup = AugmentNameLookup.of(CATALOG, List.of("Shrink Engine"));

        // then
        assertThat(lookup.augments()).extracting(Augment::augmentId).containsExactly(1373);
    }

    @Test
    @DisplayName("앞뒤 공백과 대소문자는 무시한다")
    void of_IgnoresSurroundingSpacesAndCase() {
        // when
        AugmentNameLookup lookup = AugmentNameLookup.of(CATALOG, List.of("  shrink ENGINE "));

        // then
        assertThat(lookup.augments()).extracting(Augment::augmentId).containsExactly(1373);
    }

    @Test
    @DisplayName("이름 안의 공백이 다르면 찾지 못한다")
    void of_RequiresExactInnerText() {
        // when
        AugmentNameLookup lookup = AugmentNameLookup.of(CATALOG, List.of("축소엔진"));

        // then
        assertThat(lookup.augments()).isEmpty();
        assertThat(lookup.unmatchedNames()).containsExactly("축소엔진");
    }

    @Test
    @DisplayName("찾은 증강은 요청 순서를 유지한다")
    void of_KeepsRequestOrder() {
        // when
        AugmentNameLookup lookup = AugmentNameLookup.of(CATALOG, List.of("되풀이", "축소 엔진"));

        // then
        assertThat(lookup.augments()).extracting(Augment::augmentId).containsExactly(1402, 1373);
    }

    @Test
    @DisplayName("못 찾은 이름은 받은 글자 그대로 돌려준다")
    void of_KeepsUnmatchedNameAsReceived() {
        // when
        AugmentNameLookup lookup = AugmentNameLookup.of(CATALOG, List.of(" 없는증강 ", "되풀이"));

        // then
        assertThat(lookup.unmatchedNames()).containsExactly(" 없는증강 ");
        assertThat(lookup.augments()).extracting(Augment::augmentId).containsExactly(1402);
    }
}
