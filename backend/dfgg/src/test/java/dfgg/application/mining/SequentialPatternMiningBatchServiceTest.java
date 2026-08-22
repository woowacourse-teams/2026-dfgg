package dfgg.application.mining;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.sequence.PrefixSpanMiner;
import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.sequence.SequentialPattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SequentialPatternMiningBatchService.class, PrefixSpanMiner.class})
class SequentialPatternMiningBatchServiceTest {

    @Autowired
    private SequentialPatternMiningBatchService miningBatchService;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Autowired
    private MatchParticipantCohortRepository cohortRepository;

    @BeforeEach
    void cleanUp() {
        participantRepository.deleteAllInBatch();
        cohortRepository.deleteAllInBatch();
    }

    @Test
    void 실_매치_데이터와_코호트를_조인해_챔피언_포지션_티어_패치_스코프별로_시퀀스를_마이닝한다() {
        // given
        for (int i = 0; i < 10; i++) {
            seedParticipantWithCohort("KR_MATCH_" + i, "GOLD");
        }
        for (int i = 0; i < 3; i++) {
            seedParticipantWithCohort("KR_LOW_SUPPORT_" + i, "PLATINUM");
        }

        // when
        Map<MiningScope, List<SequentialPattern>> patternsByScope =
                miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 5);

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
                miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 1);

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
                miningBatchService.mineFromMatchData("RANKED_SOLO_5x5", 1);

        // then
        assertThat(patternsByScope).containsKey(new MiningScope(1L, "TOP", "GOLD", "14.1"));
        assertThat(patternsByScope).doesNotContainKey(new MiningScope(1L, "TOP", "PLATINUM", "14.1"));
    }

    private void seedParticipantWithCohort(String matchId, String tier) {
        NormalizedMatch match = new NormalizedMatch(matchId, "14.1", 420, List.of());
        String puuid = "puuid-" + matchId;
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
                matchId,
                puuid,
                "RANKED_SOLO_5x5",
                tier,
                "II",
                Instant.now()
        ));
    }
}
