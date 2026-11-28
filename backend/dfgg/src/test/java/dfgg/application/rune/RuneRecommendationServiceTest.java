package dfgg.application.rune;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.champion.ChampionService;
import dfgg.domain.champion.Champion;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.language.Language;
import dfgg.domain.rune.NormalizedMatchParticipantRune;
import dfgg.domain.rune.NormalizedMatchParticipantRuneRepository;
import dfgg.domain.rune.RunePage;
import dfgg.domain.rune.RuneRepository;
import dfgg.domain.rune.StatShards;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.response.RuneRecommendationResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuneRecommendationServiceTest {

    private static final int JAYCE_ID = 126;
    private static final int MINIMUM_GAMES = 3;
    private static final RunePage CONQUEROR_PAGE = new RunePage(8000, List.of(8010, 9111, 9104, 8299),
            8400, List.of(8444, 8242), new StatShards(5005, 5008, 5001));

    private final ChampionService champions = mock(ChampionService.class);
    private final RuneRecommendationPatch recommendationPatch = mock(RuneRecommendationPatch.class);
    private final NormalizedMatchParticipantRuneRepository participantRunes =
            mock(NormalizedMatchParticipantRuneRepository.class);
    private final RuneRepository runes = mock(RuneRepository.class);
    private final ImageUrls imageUrls = mock(ImageUrls.class);

    private RuneRecommendationService service;

    @BeforeEach
    void setUp() {
        Champion jayce = mock(Champion.class);
        when(jayce.getChampionId()).thenReturn((long) JAYCE_ID);
        when(champions.findChampionByName("Jayce")).thenReturn(jayce);
        when(participantRunes.findByChampionIdAndPositionInAndPatch(anyInt(), anyList(), anyString()))
                .thenReturn(List.of());
        service = new RuneRecommendationService(champions, new ChampionPositionNormalizer(), recommendationPatch,
                participantRunes, runes, imageUrls, MINIMUM_GAMES, 1, 3);
    }

    @Test
    @DisplayName("최신 패치 기록이 기준보다 적으면 이전 패치 기록으로 추천한다")
    void recommend_WhenLatestPatchBelowMinimum_FallsBackToPreviousPatch() {
        // given
        when(recommendationPatch.candidates()).thenReturn(List.of("16.23", "16.22"));
        givenGames("16.23", 1);
        givenGames("16.22", MINIMUM_GAMES);

        // when
        RuneRecommendationResponse response = service.recommend(jayceMid(), Language.KO_KR);

        // then
        assertThat(response.patch()).isEqualTo("16.22");
        assertThat(response.championGames()).isEqualTo(MINIMUM_GAMES);
        assertThat(response.pages()).hasSize(1);
    }

    @Test
    @DisplayName("최신 패치에서 추천이 나오면 이전 패치는 보지 않는다")
    void recommend_WhenLatestPatchEnough_UsesLatestPatch() {
        // given
        when(recommendationPatch.candidates()).thenReturn(List.of("16.23", "16.22"));
        givenGames("16.23", MINIMUM_GAMES);
        givenGames("16.22", MINIMUM_GAMES + 10);

        // when
        RuneRecommendationResponse response = service.recommend(jayceMid(), Language.KO_KR);

        // then
        assertThat(response.patch()).isEqualTo("16.23");
        assertThat(response.championGames()).isEqualTo(MINIMUM_GAMES);
    }

    @Test
    @DisplayName("모든 후보 패치에서 추천이 안 나오면 최신 패치와 그 판 수를 빈 페이지로 낸다")
    void recommend_WhenNoPatchEnough_ReturnsLatestPatchWithEmptyPages() {
        // given
        when(recommendationPatch.candidates()).thenReturn(List.of("16.23", "16.22"));
        givenGames("16.23", 2);
        givenGames("16.22", 1);

        // when
        RuneRecommendationResponse response = service.recommend(jayceMid(), Language.KO_KR);

        // then
        assertThat(response.patch()).isEqualTo("16.23");
        assertThat(response.championGames()).isEqualTo(2);
        assertThat(response.pages()).isEmpty();
    }

    @Test
    @DisplayName("룬 기록이 하나도 없으면 패치 없이 빈 응답을 낸다")
    void recommend_WhenNoCandidates_ReturnsWithoutPatch() {
        // given
        when(recommendationPatch.candidates()).thenReturn(List.of());

        // when
        RuneRecommendationResponse response = service.recommend(jayceMid(), Language.KO_KR);

        // then
        assertThat(response).isEqualTo(RuneRecommendationResponse.withoutPatch());
    }

    private void givenGames(String patch, int games) {
        List<NormalizedMatchParticipantRune> records = Collections.nCopies(games,
                new NormalizedMatchParticipantRune("KR_" + patch, 1, patch, JAYCE_ID, 100, "MIDDLE", true,
                        CONQUEROR_PAGE));
        when(participantRunes.findByChampionIdAndPositionInAndPatch(eq(JAYCE_ID), anyList(), eq(patch)))
                .thenReturn(records);
    }

    private ChampionDto jayceMid() {
        return new ChampionDto("Jayce", "MID");
    }
}
