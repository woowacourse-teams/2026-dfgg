package dfgg.domain.score;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * 채점기가 매긴 점수에 MVP·ACE를 붙인다. 모드와 관계없이 같다.
 * 동점은 반올림 전 점수 → KP → 데스가 적은 순으로 가른다.
 */
public class MatchScores {

    private static final Comparator<Candidate> BADGE_ORDER = Comparator.comparing(Candidate::score)
            .thenComparingDouble(Candidate::killParticipation)
            .thenComparing(Comparator.comparingInt(Candidate::deaths).reversed());

    private final List<ParticipantScore> results;

    public MatchScores(ScoringParticipants participants, List<DfScore> scores) {
        List<ScoringParticipant> members = participants.mapEach(Function.identity());
        List<Candidate> candidates = IntStream.range(0, members.size())
                .mapToObj(index -> new Candidate(index, members.get(index), scores.get(index),
                        participants.teamContributionOf(members.get(index)).killParticipation()))
                .toList();
        int mvpIndex = bestOf(candidates, true);
        int aceIndex = bestOf(candidates, false);
        this.results = candidates.stream()
                .map(candidate -> new ParticipantScore(candidate.score(), badgeOf(candidate.index(), mvpIndex, aceIndex)))
                .toList();
    }

    private int bestOf(List<Candidate> candidates, boolean winners) {
        return candidates.stream()
                .filter(candidate -> candidate.participant().isWinner() == winners)
                .max(BADGE_ORDER)
                .orElseThrow()
                .index();
    }

    private Badge badgeOf(int index, int mvpIndex, int aceIndex) {
        if (index == mvpIndex) {
            return Badge.MVP;
        }
        if (index == aceIndex) {
            return Badge.ACE;
        }
        return null;
    }

    /** 참가자 순서와 같다. */
    public List<ParticipantScore> results() {
        return results;
    }

    private record Candidate(int index, ScoringParticipant participant, DfScore score, double killParticipation) {

        int deaths() {
            return participant.deaths();
        }
    }
}
