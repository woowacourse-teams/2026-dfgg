package dfgg.domain.item.trait;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemTraitTest {

    @Test
    @DisplayName("표시명은 한국어와 영어를 함께 갖는다")
    void getDisplayName_CarriesKoreanAndEnglish() {
        // when & then
        assertThat(ItemTrait.MIKAEL_BLESSING.getDisplayName())
                .containsEntry("ko-KR", "CC 해제 및 회복")
                .containsEntry("en-US", "CC Cleanse & Heal");
    }

    @Test
    @DisplayName("한국어 표시명이 있는 특성은 영어 표시명도 있다 — 빠뜨리면 영어 응답에 한글이 섞여 나간다")
    void getDisplayName_WhenKoreanExists_EnglishExistsToo() {
        // when
        List<ItemTrait> missingEnglish = Arrays.stream(ItemTrait.values())
                .filter(trait -> !trait.getDisplayName().get("ko-KR").isBlank())
                .filter(trait -> trait.getDisplayName().get("en-US").isBlank())
                .toList();

        // then
        assertThat(missingEnglish).isEmpty();
    }

    @Test
    @DisplayName("영어 표시명에는 한글이 없다")
    void getDisplayName_EnglishContainsNoHangul() {
        // when & then
        assertThat(ItemTrait.values()).allSatisfy(trait ->
                assertThat(trait.getDisplayName().get("en-US")).as(trait.name()).doesNotContainPattern("[가-힣]"));
    }
}
