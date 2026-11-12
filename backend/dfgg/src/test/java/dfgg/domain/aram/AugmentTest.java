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

    @Test
    @DisplayName("요청한 언어의 설명을 준다")
    void descriptionIn_WhenDescribed_ReturnsDescription() {
        // given
        Augment augment = new Augment("16.22", 1007, Map.of("ko-KR", "육중한 힘", "en-US", "Blunt Force"),
                AugmentRarity.SILVER);
        augment.describe(Map.of("ko-KR", "공격력이 20% 증가합니다.", "en-US", "Gain 20% Attack Damage."));

        // when
        Optional<String> description = augment.descriptionIn(Language.EN_US);

        // then
        assertThat(description).contains("Gain 20% Attack Damage.");
    }

    @Test
    @DisplayName("요청한 언어의 설명이 없으면 한글 설명을 준다")
    void descriptionIn_WhenLanguageMissing_FallsBackToKorean() {
        // given
        Augment augment = new Augment("16.22", 1007, Map.of("ko-KR", "육중한 힘"), AugmentRarity.SILVER);
        augment.describe(Map.of("ko-KR", "공격력이 20% 증가합니다."));

        // when
        Optional<String> description = augment.descriptionIn(Language.EN_US);

        // then
        assertThat(description).contains("공격력이 20% 증가합니다.");
    }

    @Test
    @DisplayName("설명을 붙이지 않았으면 설명이 없다")
    void descriptionIn_WhenNotDescribed_IsEmpty() {
        // given
        Augment augment = new Augment("16.22", 1154, Map.of("ko-KR", "우르프의 챔피언"), AugmentRarity.PRISMATIC);

        // when
        Optional<String> description = augment.descriptionIn(Language.KO_KR);

        // then
        assertThat(description).isEmpty();
    }

    @Test
    @DisplayName("빈 설명을 붙이면 설명이 없다")
    void describe_WhenEmpty_LeavesNoDescription() {
        // given
        Augment augment = new Augment("16.22", 1154, Map.of("ko-KR", "우르프의 챔피언"), AugmentRarity.PRISMATIC);

        // when
        augment.describe(Map.of());

        // then
        assertThat(augment.descriptionIn(Language.KO_KR)).isEmpty();
    }

    @Test
    @DisplayName("영어 설명만 있으면 한국어 요청에는 대신할 한글 설명이 없어 비어 있다")
    void descriptionIn_WhenOnlyEnglish_IsEmptyForKorean() {
        // given
        Augment augment = new Augment("16.22", 1007, Map.of("ko-KR", "육중한 힘"), AugmentRarity.SILVER);
        augment.describe(Map.of("en-US", "Gain 20% Attack Damage."));

        // when
        Optional<String> description = augment.descriptionIn(Language.KO_KR);

        // then
        assertThat(description).isEmpty();
    }
}
