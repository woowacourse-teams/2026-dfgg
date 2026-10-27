package dfgg.application.recommend.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompositionFeatureCalculatorTest {

    private static final long MALPHITE = 54L;    // TANK
    private static final long AHRI = 103L;       // MAGE, ASSASSIN
    private static final long JINX = 222L;       // MARKSMAN
    private static final long DARIUS = 122L;     // TANK, FIGHTER
    private static final long THRESH = 412L;     // SUPPORT, TANK
    private static final long NO_TAG = 777L;     // 태그 없음
    private static final long UNREGISTERED = 999L;

    private ChampionRepository repository;
    private CompositionFeatureCalculator calculator;

    @BeforeEach
    void setUp() {
        repository = mock(ChampionRepository.class);
        var champions = List.of(champion(MALPHITE, ChampionTag.TANK),
                champion(AHRI, ChampionTag.MAGE, ChampionTag.ASSASSIN), champion(JINX, ChampionTag.MARKSMAN),
                champion(DARIUS, ChampionTag.TANK, ChampionTag.FIGHTER),
                champion(THRESH, ChampionTag.SUPPORT, ChampionTag.TANK), champion(NO_TAG));
        // 실제 쿼리처럼 IN 조회는 챔피언당 한 행이다. 요청에 같은 ID가 여러 번 있어도 한 번만 돌려준다.
        when(repository.findAllWithTagsByChampionIdIn(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return champions.stream().filter(c -> ids.contains(c.getChampionId())).toList();
        });
        calculator = new CompositionFeatureCalculator(repository);
    }

    @Test
    @DisplayName("미리 읽은 태그로 센 결과가 매번 조회해 센 결과와 같다 — 일반 5대5")
    void calculate_WhenTagsPreloaded_MatchesPerCallLookup_ForOrdinaryTeams() {
        assertSameResult(List.of(MALPHITE, AHRI, JINX, THRESH), List.of(DARIUS, AHRI, JINX, MALPHITE, THRESH));
    }

    @Test
    @DisplayName("같은 챔피언이 한쪽에 여러 번 있어도 한 번만 센다 — IN 조회가 챔피언당 한 행이기 때문이다")
    void calculate_WhenSameChampionRepeatsOnOneSide_CountsItOnce() {
        var allies = List.of(MALPHITE, MALPHITE, JINX);
        var enemies = List.of(DARIUS, DARIUS, DARIUS);

        assertSameResult(allies, enemies);
        assertThat(calculator.calculate(allies, enemies, calculator.readTags(union(allies, enemies))).allyTankCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 챔피언이 아군과 적군에 모두 있으면 양쪽에서 각각 센다")
    void calculate_WhenSameChampionOnBothSides_CountsOnEachSide() {
        var features = calculator.calculate(List.of(MALPHITE), List.of(MALPHITE),
                calculator.readTags(List.of(MALPHITE)));

        assertThat(features.allyTankCount()).isEqualTo(1);
        assertThat(features.enemyTankCount()).isEqualTo(1);
        assertSameResult(List.of(MALPHITE), List.of(MALPHITE));
    }

    @Test
    @DisplayName("태그가 없는 챔피언은 0 기여다")
    void calculate_WhenChampionHasNoTags_ContributesZero() {
        var allies = List.of(NO_TAG);
        var enemies = List.of(NO_TAG, JINX);

        assertSameResult(allies, enemies);
        assertThat(calculator.calculate(allies, enemies, calculator.readTags(union(allies, enemies))).values())
                .containsExactly(0., 0., 1., 0., 0., 0., 0., 0., 0.);
    }

    @Test
    @DisplayName("DB에 없는 챔피언은 0 기여다")
    void calculate_WhenChampionIsUnregistered_ContributesZero() {
        var allies = List.of(UNREGISTERED, MALPHITE);
        var enemies = List.of(UNREGISTERED);

        assertSameResult(allies, enemies);
        assertThat(calculator.calculate(allies, enemies, calculator.readTags(union(allies, enemies))).values())
                .containsExactly(0., 0., 0., 0., 0., 1., 0., 0., 0.);
    }

    @Test
    @DisplayName("아군이나 적군이 비어 있어도 같다")
    void calculate_WhenOneSideIsEmpty_MatchesPerCallLookup() {
        assertSameResult(List.of(), List.of(MALPHITE, AHRI));
        assertSameResult(List.of(MALPHITE, AHRI), List.of());
        assertSameResult(List.of(), List.of());
    }

    @Test
    @DisplayName("태그를 읽을 챔피언이 없으면 조회하지 않는다")
    void readTags_WhenNoChampionIds_DoesNotQuery() {
        assertThat(calculator.readTags(List.of())).isEmpty();

        verify(repository, times(0)).findAllWithTagsByChampionIdIn(any());
    }

    @Test
    @DisplayName("미리 읽은 태그로 세면 챔피언 조회를 하지 않는다")
    void calculate_WhenTagsPreloaded_DoesNotQueryAgain() {
        var tags = calculator.readTags(List.of(MALPHITE, AHRI, JINX));
        org.mockito.Mockito.clearInvocations(repository);

        calculator.calculate(List.of(MALPHITE), List.of(AHRI, JINX), tags);

        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    private void assertSameResult(List<Long> allies, List<Long> enemies) {
        var perCall = calculator.calculate(allies, enemies);
        var preloaded = calculator.calculate(allies, enemies, calculator.readTags(union(allies, enemies)));

        assertThat(preloaded).isEqualTo(perCall);
    }

    private List<Long> union(List<Long> allies, List<Long> enemies) {
        return Stream.concat(allies.stream(), enemies.stream()).distinct().toList();
    }

    private Champion champion(long id, ChampionTag... tags) {
        return new Champion(id, "C" + id, Map.of("en", "C" + id), new ArrayList<>(List.of(tags)));
    }
}
