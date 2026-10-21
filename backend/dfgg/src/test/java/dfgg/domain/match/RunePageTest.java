package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunePageTest {

    private static final int RESOLVE = 8400;
    private static final int INSPIRATION = 8300;
    private static final List<Integer> RESOLVE_PERKS = List.of(8437, 8446, 8473, 8451);
    private static final List<Integer> INSPIRATION_PERKS = List.of(8304, 8345);
    private static final StatShards SHARDS = new StatShards(5005, 5008, 5001);

    @Test
    @DisplayName("주 계열의 첫 번째 룬이 핵심 룬이다")
    void keystoneId_ReturnsFirstPrimaryPerk() {
        // given
        RunePage runePage = new RunePage(RESOLVE, RESOLVE_PERKS, INSPIRATION, INSPIRATION_PERKS, SHARDS);

        // when & then
        assertThat(runePage.keystoneId()).isEqualTo(8437);
    }

    @Test
    @DisplayName("주 계열 룬이 4개가 아니면 룬 페이지를 만들 수 없다")
    void create_WhenPrimaryPerkCountIsNotFour_Throws() {
        assertThatThrownBy(() -> new RunePage(RESOLVE, List.of(8437, 8446, 8473), INSPIRATION,
                INSPIRATION_PERKS, SHARDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주 계열 룬은 4개");
    }

    @Test
    @DisplayName("보조 계열 룬이 2개가 아니면 룬 페이지를 만들 수 없다")
    void create_WhenSubPerkCountIsNotTwo_Throws() {
        assertThatThrownBy(() -> new RunePage(RESOLVE, RESOLVE_PERKS, INSPIRATION, List.of(8304), SHARDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("보조 계열 룬은 2개");
    }

    @Test
    @DisplayName("주 계열과 보조 계열이 같으면 룬 페이지를 만들 수 없다")
    void create_WhenPrimaryAndSubStyleAreSame_Throws() {
        assertThatThrownBy(() -> new RunePage(RESOLVE, RESOLVE_PERKS, RESOLVE, List.of(8444, 8242), SHARDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주 계열과 보조 계열은 달라야");
    }

    @Test
    @DisplayName("룬 ID가 양수가 아니면 룬 페이지를 만들 수 없다")
    void create_WhenPerkIdIsNotPositive_Throws() {
        assertThatThrownBy(() -> new RunePage(RESOLVE, List.of(8437, 0, 8473, 8451), INSPIRATION,
                INSPIRATION_PERKS, SHARDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");
    }

    @Test
    @DisplayName("계열 ID가 양수가 아니면 룬 페이지를 만들 수 없다")
    void create_WhenStyleIdIsNotPositive_Throws() {
        assertThatThrownBy(() -> new RunePage(0, RESOLVE_PERKS, INSPIRATION, INSPIRATION_PERKS, SHARDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");
    }

    @Test
    @DisplayName("만든 뒤 넘겨준 룬 목록을 바꿔도 룬 페이지는 바뀌지 않는다")
    void create_CopiesPerkLists() {
        // given
        List<Integer> primaryPerks = new ArrayList<>(RESOLVE_PERKS);
        RunePage runePage = new RunePage(RESOLVE, primaryPerks, INSPIRATION, INSPIRATION_PERKS, SHARDS);

        // when
        primaryPerks.set(0, 8439);

        // then
        assertThat(runePage.keystoneId()).isEqualTo(8437);
    }

    @Test
    @DisplayName("파편 ID가 양수가 아니면 파편을 만들 수 없다")
    void createStatShards_WhenIdIsNotPositive_Throws() {
        assertThatThrownBy(() -> new StatShards(5005, 5008, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");
    }
}
