package dfgg.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageKeysTest {

    @Test
    @DisplayName("챔피언 이미지 키는 버전 폴더 아래 영문 키로 만든다")
    void champion_WhenVersionAndRiotKeyGiven_BuildKeyUnderVersionFolder() {
        // when
        String key = ImageKeys.champion("16.18", "MonkeyKing");

        // then
        assertThat(key).isEqualTo("dfgg/images/16.18/champions/MonkeyKing.png");
    }

    @Test
    @DisplayName("아이템 이미지 키는 버전 폴더 아래 아이템 id로 만든다")
    void item_WhenVersionAndItemIdGiven_BuildKeyUnderVersionFolder() {
        // when
        String key = ImageKeys.item("16.18", 3031L);

        // then
        assertThat(key).isEqualTo("dfgg/images/16.18/items/3031.png");
    }
}
