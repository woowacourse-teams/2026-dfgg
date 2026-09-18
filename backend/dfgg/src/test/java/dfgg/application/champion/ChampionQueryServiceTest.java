package dfgg.application.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.image.ImageUrls;
import dfgg.presentation.dto.ChampionSummaryDto;
import dfgg.presentation.dto.response.ChampionsResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionQueryServiceTest {

    @Mock
    private ChampionRepository championRepository;

    @Test
    @DisplayName("한글 이름 가나다순으로 정렬한다 — 저장 순서·영문 키·id 순서와 무관하다")
    void findAll_WhenChampionsUnsorted_SortByKoreanName() {
        // given
        when(championRepository.findAll()).thenReturn(List.of(
                new Champion(266L, "Aatrox", Map.of("ko-KR", "아트록스"), List.of()),
                new Champion(86L, "Garen", Map.of("ko-KR", "가렌"), List.of()),
                new Champion(62L, "MonkeyKing", Map.of("ko-KR", "손오공"), List.of())
        ));
        ChampionQueryService service = new ChampionQueryService(
                championRepository, new ImageUrls("test-bucket", "ap-northeast-2", "99.1"));

        // when
        ChampionsResponse response = service.findAll();

        // then
        assertThat(response.champions())
                .extracting(ChampionSummaryDto::name)
                .containsExactly("가렌", "손오공", "아트록스");
    }
}
