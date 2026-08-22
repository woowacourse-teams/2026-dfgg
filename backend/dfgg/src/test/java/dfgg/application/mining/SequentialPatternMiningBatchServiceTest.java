package dfgg.application.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.sequence.PrefixSpanMiner;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import dfgg.domain.sequence.SequentialPattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SequentialPatternMiningBatchService.class, PrefixSpanMiner.class, ChampionPositionNormalizer.class})
class SequentialPatternMiningBatchServiceTest {

    private static final String ALGORITHM_VERSION = "prefixspan-v1";

    @Autowired
    private SequentialPatternMiningBatchService miningBatchService;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Autowired
    private MatchParticipantCohortRepository cohortRepository;

    @Autowired
    private MinedSequentialPatternRepository minedSequentialPatternRepository;

    @BeforeEach
    void cleanUp() {
        participantRepository.deleteAllInBatch();
        cohortRepository.deleteAllInBatch();
        minedSequentialPatternRepository.deleteAllInBatch();
    }

    @Test
    void 실_매치_데이터와_코호트를_조인해_챔피언_포지션_티어_패치_스코프별로_시퀀스를_마이닝한다() {
        // given
        for (int i = 0; i < 10; i++) {
            seedParticipantWithCohort("KR_MATCH_" + i, "GOLD", true);
        }
        for (int i = 0; i < 3; i++) {
            seedParticipantWithCohort("KR_LOW_SUPPORT_" + i, "PLATINUM", true);
        }

        // when
        Map<MiningScope, List<SequentialPattern>> patternsByScope =
                miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 5, ALGORITHM_VERSION);

        // then
        MiningScope goldTopScope = new MiningScope(1L, "TOP", "GOLD", "14.1");
        assertThat(patternsByScope).containsKey(goldTopScope);
        assertThat(patternsByScope.get(goldTopScope))
                .contains(new SequentialPattern(List.of(3071L, 6653L), 10));

        MiningScope platinumTopScope = new MiningScope(1L, "TOP", "PLATINUM", "14.1");
        assertThat(patternsByScope.getOrDefault(platinumTopScope, List.of()))
                .doesNotContain(new SequentialPattern(List.of(3071L, 6653L), 3));
    }

    @Test
    void 코호트_정보가_없는_참가자는_마이닝에서_제외한다() {
        // given
        NormalizedMatch match = new NormalizedMatch("KR_NO_COHORT", "14.1", 420, List.of());
        participantRepository.save(new NormalizedMatchParticipant(match, new NormalizedParticipant(
                "puuid-no-cohort",
                1,
                1,
                100,
                "TOP",
                true,
                List.of(3071, 6653),
                List.of(3071, 6653),
                true
        )));

        // when
        Map<MiningScope, List<SequentialPattern>> patternsByScope =
                miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 1, ALGORITHM_VERSION);

        // then
        assertThat(patternsByScope).isEmpty();
    }

    @Test
    void 같은_매치와_puuid라도_요청한_큐_타입의_코호트만_사용해_티어를_판단한다() {
        // given
        NormalizedMatch match = new NormalizedMatch("KR_MULTI_QUEUE", "14.1", 420, List.of());
        String puuid = "puuid-multi-queue";
        participantRepository.save(new NormalizedMatchParticipant(match, new NormalizedParticipant(
                puuid,
                1,
                1,
                100,
                "TOP",
                true,
                List.of(3071, 6653),
                List.of(3071, 6653),
                true
        )));
        cohortRepository.save(new MatchParticipantCohort(
                "KR_MULTI_QUEUE", puuid, "RANKED_SOLO_5x5", "GOLD", "II", Instant.now()
        ));
        cohortRepository.save(new MatchParticipantCohort(
                "KR_MULTI_QUEUE", puuid, "RANKED_FLEX_SR", "PLATINUM", "IV", Instant.now()
        ));

        // when
        Map<MiningScope, List<SequentialPattern>> patternsByScope =
                miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 1, ALGORITHM_VERSION);

        // then
        assertThat(patternsByScope).containsKey(new MiningScope(1L, "TOP", "GOLD", "14.1"));
        assertThat(patternsByScope).doesNotContainKey(new MiningScope(1L, "TOP", "PLATINUM", "14.1"));
    }

    @Test
    @DisplayName("최소 지지도를 만족하는 패턴을 스코프 통계와 함께 저장한다")
    void mineFromMatchData_WhenPatternMeetsMinSupport_PersistsMinedSequentialPatternWithScopeCounts() {
        // given
        for (int i = 0; i < 10; i++) {
            seedParticipantWithCohort("KR_MATCH_" + i, "GOLD", true);
        }

        // when
        miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 5, ALGORITHM_VERSION);

        // then
        MinedSequentialPattern saved = minedSequentialPatternRepository.findAll().stream()
                .filter(pattern -> pattern.getItems().equals(List.of(3071L, 6653L)))
                .findFirst()
                .orElseThrow();
        assertThat(saved.getChampionId()).isEqualTo(1L);
        assertThat(saved.getPosition()).isEqualTo(ChampionPosition.TOP);
        assertThat(saved.getTier()).isEqualTo("GOLD");
        assertThat(saved.getPatch()).isEqualTo("14.1");
        assertThat(saved.getSupportCount()).isEqualTo(10);
        assertThat(saved.getScopeTotalCount()).isEqualTo(10);
        assertThat(saved.getWinCount()).isEqualTo(10);
        assertThat(saved.getAlgorithmVersion()).isEqualTo(ALGORITHM_VERSION);
    }

    @Test
    @DisplayName("일부 참가자가 패배했다면 승리한 참가자만 승리 횟수로 집계한다")
    void mineFromMatchData_WhenSomeParticipantsLost_CountsOnlyWinnersAsWinCount() {
        // given: 4명이 같은 시퀀스를 만들지만 그중 1명만 패배한다
        seedParticipantWithCohort("KR_WIN_1", "GOLD", true);
        seedParticipantWithCohort("KR_WIN_2", "GOLD", true);
        seedParticipantWithCohort("KR_WIN_3", "GOLD", true);
        seedParticipantWithCohort("KR_LOSE_1", "GOLD", false);

        // when
        miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 3, ALGORITHM_VERSION);

        // then
        MinedSequentialPattern saved = minedSequentialPatternRepository.findAll().stream()
                .filter(pattern -> pattern.getItems().equals(List.of(3071L, 6653L)))
                .findFirst()
                .orElseThrow();
        assertThat(saved.getSupportCount()).isEqualTo(4);
        assertThat(saved.getScopeTotalCount()).isEqualTo(4);
        assertThat(saved.getWinCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Riot API 원시 포지션 값은 정규화한 뒤 저장한다")
    void mineFromMatchData_WhenPositionIsRiotRawValue_NormalizesBeforePersisting() {
        // given: Riot API가 실제로 내려주는 원시 포지션 값(MIDDLE)으로 5명을 시드한다
        for (int i = 0; i < 5; i++) {
            seedParticipantWithCohortAndPosition("KR_MID_" + i, "GOLD", "MIDDLE", true);
        }

        // when
        miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 3, ALGORITHM_VERSION);

        // then
        MinedSequentialPattern saved = minedSequentialPatternRepository.findAll().stream()
                .filter(pattern -> pattern.getItems().equals(List.of(3071L, 6653L)))
                .findFirst()
                .orElseThrow();
        assertThat(saved.getPosition()).isEqualTo(ChampionPosition.MID);
    }

    @Test
    @DisplayName("같은 algorithmVersion으로 다시 실행하면 기존 패턴을 중복 없이 교체한다")
    void mineFromMatchData_WhenRunTwiceWithSameAlgorithmVersion_ReplacesPreviousPatternsWithoutDuplicates() {
        // given
        for (int i = 0; i < 10; i++) {
            seedParticipantWithCohort("KR_MATCH_" + i, "GOLD", true);
        }
        miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 5, ALGORITHM_VERSION);
        int firstRunCount = minedSequentialPatternRepository.findAll().size();

        // when
        miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 5, ALGORITHM_VERSION);

        // then
        assertThat(minedSequentialPatternRepository.findAll()).hasSize(firstRunCount);
    }

    @Test
    @DisplayName("algorithmVersion이 비어 있으면 예외가 발생한다")
    void mineFromMatchData_WhenAlgorithmVersionIsBlank_ThrowsIllegalArgumentException() {
        // given & when & then
        assertThatThrownBy(() -> miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 1, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void seedParticipantWithCohort(String matchId, String tier, boolean win) {
        seedParticipantWithCohortAndPosition(matchId, tier, "TOP", win);
    }

    private void seedParticipantWithCohortAndPosition(String matchId, String tier, String position, boolean win) {
        NormalizedMatch match = new NormalizedMatch(matchId, "14.1", 420, List.of());
        String puuid = "puuid-" + matchId;
        participantRepository.save(new NormalizedMatchParticipant(match, new NormalizedParticipant(
                puuid,
                1,
                1,
                100,
                position,
                win,
                List.of(3071, 6653),
                List.of(3071, 6653),
                true
        )));
        cohortRepository.save(new MatchParticipantCohort(
                matchId,
                puuid,
                "RANKED_SOLO_5x5",
                tier,
                "II",
                Instant.now()
        ));
    }
}
