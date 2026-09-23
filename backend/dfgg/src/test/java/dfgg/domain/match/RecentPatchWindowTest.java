package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecentPatchWindowTest {

    @Test
    @DisplayName("최신 N개 패치만 윈도에 넣는다")
    void of_WhenMorePatchesThanWindowSize_KeepsOnlyTheNewestN() {
        // given
        List<String> observed = List.of("16.13", "16.14", "16.15", "16.16", "16.17");

        // when
        RecentPatchWindow window = RecentPatchWindow.of(observed, 3);

        // then
        assertThat(window.patches()).containsExactlyInAnyOrder("16.15", "16.16", "16.17");
    }

    @Test
    @DisplayName("최신 판정이 숫자 비교로 이뤄진다 — 문자열 정렬이면 16.9가 16.10보다 최신으로 잘못 뽑힌다")
    void of_WhenPatchesHaveDifferentMinorDigitCounts_SelectsByNumericOrder() {
        // given
        List<String> observed = List.of("16.8", "16.9", "16.10", "16.11");

        // when
        RecentPatchWindow window = RecentPatchWindow.of(observed, 2);

        // then
        assertThat(window.patches()).containsExactlyInAnyOrder("16.10", "16.11");
        assertThat(window.patches()).doesNotContain("16.9");
    }

    @Test
    @DisplayName("시즌이 섞여 있어도 메이저 버전을 먼저 본다")
    void of_WhenSeasonsAreMixed_SelectsAcrossMajorVersionsCorrectly() {
        // given
        List<String> observed = List.of("15.24", "16.1", "16.2", "14.20");

        // when
        RecentPatchWindow window = RecentPatchWindow.of(observed, 2);

        // then
        assertThat(window.patches()).containsExactlyInAnyOrder("16.1", "16.2");
    }

    @Test
    @DisplayName("관측된 패치가 윈도 크기보다 적으면 전부 포함한다")
    void of_WhenFewerPatchesThanWindowSize_KeepsAll() {
        // given
        List<String> observed = List.of("16.16", "16.17");

        // when
        RecentPatchWindow window = RecentPatchWindow.of(observed, 5);

        // then
        assertThat(window.patches()).containsExactlyInAnyOrder("16.16", "16.17");
    }

    @Test
    @DisplayName("특정 패치가 최근 윈도에 드는지 판정한다 — 집계가 _recent 카운트를 올릴지 결정하는 기준")
    void contains_WhenPatchIsInsideOrOutsideWindow_AnswersCorrectly() {
        // given
        RecentPatchWindow window = RecentPatchWindow.of(List.of("16.15", "16.16", "16.17", "16.2"), 3);

        // when & then
        assertThat(window.contains("16.17")).isTrue();
        assertThat(window.contains("16.15")).isTrue();
        assertThat(window.contains("16.2")).isFalse();
    }

    @Test
    @DisplayName("중복된 패치가 들어와도 고유 패치 기준으로 센다")
    void of_WhenObservedPatchesContainDuplicates_CountsDistinctPatches() {
        // given: 참가자 행을 그대로 넘기면 같은 패치가 여러 번 들어온다
        List<String> observed = List.of("16.17", "16.17", "16.16", "16.16", "16.15", "16.14");

        // when
        RecentPatchWindow window = RecentPatchWindow.of(observed, 2);

        // then
        assertThat(window.patches()).containsExactlyInAnyOrder("16.16", "16.17");
    }

    @Test
    @DisplayName("윈도 크기가 1 미만이면 거부한다")
    void of_WhenWindowSizeIsNotPositive_ThrowsException() {
        assertThatThrownBy(() -> RecentPatchWindow.of(List.of("16.17"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("관측된 패치가 없으면 빈 윈도가 되고 어떤 패치도 최근이 아니다")
    void of_WhenNoPatchesObserved_ProducesEmptyWindow() {
        // when
        RecentPatchWindow window = RecentPatchWindow.of(List.of(), 3);

        // then
        assertThat(window.patches()).isEmpty();
        assertThat(window.contains("16.17")).isFalse();
    }
}
