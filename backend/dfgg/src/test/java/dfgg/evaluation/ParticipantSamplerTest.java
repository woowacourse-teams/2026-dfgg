package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticipantSamplerTest {

    private final ParticipantSampler sampler = new ParticipantSampler();

    /** Riot 데이터에서 participantId는 포지션과 강하게 묶여 있다: 1=TOP, 2=JUNGLE, 3=MID... */
    private List<NormalizedMatchParticipant> match() {
        List<String> positions = List.of("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY");
        return IntStream.range(0, 10)
                .mapToObj(i -> new NormalizedMatchParticipant(
                        "puuid-" + i, i + 1, 100 + i, i < 5 ? 100 : 200,
                        positions.get(i % 5), "EMERALD", true,
                        List.of(3031), List.of(3031), true))
                .toList();
    }

    @Test
    @DisplayName("여러 매치에 걸쳐 5개 포지션이 고르게 뽑힌다")
    void sample_AcrossManyMatches_CoversEveryPositionEvenly() {
        // given
        Map<String, Long> byPosition = IntStream.range(0, 500)
                .mapToObj(i -> "KR_" + i)
                .flatMap(matchId -> sampler.sample(match(), matchId, 2).stream())
                .collect(Collectors.groupingBy(NormalizedMatchParticipant::getPosition, Collectors.counting()));

        // then: 5개 포지션 모두 등장하고, 어느 하나가 절반을 넘지 않는다
        assertThat(byPosition.keySet())
                .containsExactlyInAnyOrder("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY");
        long total = byPosition.values().stream().mapToLong(Long::longValue).sum();
        assertThat(byPosition.values()).allSatisfy(count ->
                assertThat(count).isLessThan(total / 2));
    }

    @Test
    @DisplayName("양 팀에서 고루 뽑는다 — 한쪽 팀만 보면 조합 관점이 한쪽으로 치우친다")
    void sample_AcrossManyMatches_CoversBothTeams() {
        // given
        Map<Integer, Long> byTeam = IntStream.range(0, 500)
                .mapToObj(i -> "KR_" + i)
                .flatMap(matchId -> sampler.sample(match(), matchId, 2).stream())
                .collect(Collectors.groupingBy(NormalizedMatchParticipant::getTeamId, Collectors.counting()));

        // then
        assertThat(byTeam.keySet()).containsExactlyInAnyOrder(100, 200);
    }

    @Test
    @DisplayName("같은 매치는 항상 같은 참가자를 뽑는다 — 평가를 재현할 수 있어야 한다")
    void sample_WhenSameMatchId_PicksSameParticipants() {
        // when
        List<String> first = sampler.sample(match(), "KR_42", 2).stream()
                .map(NormalizedMatchParticipant::getPuuid).toList();
        List<String> second = sampler.sample(match(), "KR_42", 2).stream()
                .map(NormalizedMatchParticipant::getPuuid).toList();

        // then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("요청한 수보다 참가자가 적으면 있는 만큼만 준다")
    void sample_WhenFewerParticipantsThanRequested_ReturnsWhatExists() {
        // given
        List<NormalizedMatchParticipant> small = match().subList(0, 3);

        // when & then
        assertThat(sampler.sample(small, "KR_1", 5)).hasSize(3);
    }
}
