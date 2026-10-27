package dfgg.application.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.counter.ChampionCounter;
import dfgg.domain.counter.ChampionCounterRepository;
import dfgg.domain.counter.ChampionPositionStats;
import dfgg.domain.counter.ChampionPositionStatsRepository;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.request.CounterRequest;
import dfgg.presentation.dto.response.CounterResponse;
import dfgg.presentation.dto.response.CounterResponse.CounterPickResponse;
import dfgg.presentation.dto.response.CounterResponse.EnemyCounterResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CounterServiceTest {

    private static final int PICKS_PER_ENEMY = 5;
    private static final int AHRI = 103;
    private static final int DARIUS = 122;
    private static final int LEE_SIN = 64;
    private static final int YASUO = 157;

    @Mock
    private ChampionPositionStatsRepository positionStatsRepository;

    @Mock
    private ChampionCounterRepository counterRepository;

    @Mock
    private ChampionRepository championRepository;

    private CounterService counterService;

    @BeforeEach
    void setUp() {
        counterService = new CounterService(positionStatsRepository, counterRepository, championRepository,
                PICKS_PER_ENEMY);
        given(positionStatsRepository.findDistinctPatches()).willReturn(List.of("16.9", "16.10"));
        given(championRepository.findAllById(any())).willAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false)
                    .map(id -> new Champion(id, "Key" + id, Map.of("ko-KR", "챔피언" + id, "en-US", "Champion" + id),
                            List.of()))
                    .toList();
        });
    }

    @Test
    @DisplayName("응답의 적 순서는 요청 순서를 따른다")
    void recommend_WhenSeveralEnemies_KeepRequestOrder() {
        // given
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(DARIUS, AHRI), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        assertThat(response.enemies()).extracting(EnemyCounterResponse::championId)
                .containsExactly(DARIUS, AHRI);
    }

    @Test
    @DisplayName("기준 패치는 라인 분포 집계의 최고 패치이고, 내 포지션도 그대로 담는다")
    void recommend_WhenPositionStatsExist_UseLatestAggregatedPatch() {
        // given
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(AHRI), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        assertThat(response.patch()).isEqualTo("16.10");
        assertThat(response.myPosition()).isEqualTo(ChampionPosition.MID);
    }

    @Test
    @DisplayName("적마다 현재 패치 라인 분포를 담는다")
    void recommend_WhenPositionStatsExist_IncludeDistributionPerEnemy() {
        // given
        List<ChampionPositionStats> stored = List.of(
                positionStats(AHRI, ChampionPosition.MID, 9),
                positionStats(AHRI, ChampionPosition.TOP, 1));
        given(positionStatsRepository.findByPatchAndChampionIdIn(eq("16.10"), anyCollection()))
                .willReturn(stored);
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(AHRI), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        EnemyCounterResponse ahri = response.enemies().getFirst();
        assertThat(ahri.positions()).hasSize(5);
        assertThat(ahri.positions().getFirst().position()).isEqualTo(ChampionPosition.MID);
        assertThat(ahri.positions().getFirst().rate()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("라인 분포 집계가 없으면 기준 패치는 비고 분포는 빈 목록이다")
    void recommend_WhenNoPositionStats_ReturnEmptyDistribution() {
        // given
        given(positionStatsRepository.findDistinctPatches()).willReturn(List.of());
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(AHRI), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        assertThat(response.patch()).isNull();
        assertThat(response.enemies().getFirst().positions()).isEmpty();
    }

    @Test
    @DisplayName("카운터는 내 포지션 기준으로 읽고, 저장 순서대로 최대 5개를 담는다")
    void recommend_WhenSevenCountersStored_ReturnFirstFiveForMyPosition() {
        // given
        List<ChampionCounter> stored = counters(AHRI, ChampionPosition.MID, 1, 2, 3, 4, 5, 6, 7);
        given(counterRepository.findByEnemiesAndPosition(anyCollection(), eq(ChampionPosition.MID)))
                .willReturn(stored);
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(AHRI), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        assertThat(response.enemies().getFirst().counters()).extracting(CounterPickResponse::championId)
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("아군·적·밴에 있는 챔피언은 빼고 다음 순서로 채운다")
    void recommend_WhenCountersAlreadyTaken_SkipAndFillWithNext() {
        // given
        List<ChampionCounter> stored = counters(AHRI, ChampionPosition.MID, LEE_SIN, 1, DARIUS, 2, YASUO, 3, 4);
        given(counterRepository.findByEnemiesAndPosition(anyCollection(), eq(ChampionPosition.MID)))
                .willReturn(stored);
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(AHRI, DARIUS),
                List.of(LEE_SIN), List.of(YASUO));

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        assertThat(response.enemies().getFirst().counters()).extracting(CounterPickResponse::championId)
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("챔피언 목록에 없는 카운터는 응답에서 빼고, 그 자리는 다음 순위로 채운다")
    void recommend_WhenCounterChampionNotRegistered_SkipAndFillWithNext() {
        // given
        int unregistered = 777;
        List<ChampionCounter> stored = counters(AHRI, ChampionPosition.MID, 1, unregistered, 2, 3, 4, 5, 6);
        given(counterRepository.findByEnemiesAndPosition(anyCollection(), eq(ChampionPosition.MID)))
                .willReturn(stored);
        willAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false)
                    .filter(id -> id != unregistered)
                    .map(id -> new Champion(id, "Key" + id, Map.of("ko-KR", "챔피언" + id, "en-US", "Champion" + id),
                            List.of()))
                    .toList();
        }).given(championRepository).findAllById(any());
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(AHRI), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        assertThat(response.enemies().getFirst().counters()).extracting(CounterPickResponse::championId)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(response.enemies().getFirst().counters()).extracting(CounterPickResponse::name)
                .doesNotContainNull();
    }

    @Test
    @DisplayName("여러 적의 카운터를 한 번에 받아도 적마다 자기 카운터만 담는다")
    void recommend_WhenCountersOfSeveralEnemiesLoadedTogether_SplitByEnemy() {
        // given
        List<ChampionCounter> stored = new ArrayList<>(counters(AHRI, ChampionPosition.MID, 1, 2));
        stored.addAll(counters(DARIUS, ChampionPosition.MID, 3));
        given(counterRepository.findByEnemiesAndPosition(anyCollection(), eq(ChampionPosition.MID)))
                .willReturn(stored);
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(AHRI, DARIUS), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        assertThat(response.enemies()).extracting(EnemyCounterResponse::championId).containsExactly(AHRI, DARIUS);
        assertThat(response.enemies().get(0).counters()).extracting(CounterPickResponse::championId)
                .containsExactly(1, 2);
        assertThat(response.enemies().get(1).counters()).extracting(CounterPickResponse::championId)
                .containsExactly(3);
    }

    @Test
    @DisplayName("넣어 둔 카운터가 없으면 빈 목록이다")
    void recommend_WhenNoCounterStored_ReturnEmptyCounters() {
        // given
        given(counterRepository.findByEnemiesAndPosition(anyCollection(), any())).willReturn(List.of());
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(DARIUS), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.KO_KR);

        // then
        assertThat(response.enemies().getFirst().counters()).isEmpty();
    }

    @Test
    @DisplayName("챔피언 이름은 요청 언어로 담는다")
    void recommend_WhenLanguageGiven_UseLocalizedNames() {
        // given
        List<ChampionCounter> stored = counters(AHRI, ChampionPosition.MID, 90);
        given(counterRepository.findByEnemiesAndPosition(anyCollection(), eq(ChampionPosition.MID)))
                .willReturn(stored);
        CounterRequest request = new CounterRequest(ChampionPosition.MID, List.of(AHRI), List.of(), List.of());

        // when
        CounterResponse response = counterService.recommend(request, Language.EN_US);

        // then
        EnemyCounterResponse ahri = response.enemies().getFirst();
        assertThat(ahri.name()).isEqualTo("Champion103");
        assertThat(ahri.counters().getFirst().name()).isEqualTo("Champion90");
    }

    private static ChampionPositionStats positionStats(int championId, ChampionPosition position, int games) {
        ChampionPositionStats stats = mock(ChampionPositionStats.class);
        given(stats.getChampionId()).willReturn(championId);
        given(stats.getPosition()).willReturn(position);
        given(stats.getGames()).willReturn(games);
        return stats;
    }

    private static List<ChampionCounter> counters(int enemyId, ChampionPosition position, int... counterIds) {
        return IntStream.range(0, counterIds.length)
                .mapToObj(index -> {
                    ChampionCounter counter = mock(ChampionCounter.class);
                    given(counter.getEnemyChampionId()).willReturn(enemyId);
                    given(counter.getPosition()).willReturn(position);
                    given(counter.getCounterChampionId()).willReturn(counterIds[index]);
                    given(counter.getRank()).willReturn(index + 1);
                    return counter;
                })
                .toList();
    }
}
