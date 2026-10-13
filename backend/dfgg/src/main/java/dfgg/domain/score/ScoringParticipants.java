package dfgg.domain.score;

import static java.util.stream.Collectors.groupingBy;

import dfgg.common.exception.InvalidDfScoreRequestException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 채점할 경기의 참가자 10명. 모든 모드에 공통인 규칙(두 팀 × 5명, 킬 관여, 오브젝트 피해)을 지킨다.
 * 포지션 규칙처럼 모드마다 다른 것은 {@link MatchScorer}가 따진다.
 */
public class ScoringParticipants {

    private static final int TEAM_COUNT = 2;
    private static final int TEAM_SIZE = 5;

    private final List<ScoringParticipant> participants;

    public ScoringParticipants(List<ScoringParticipant> participants) {
        Map<Integer, List<ScoringParticipant>> teams = groupByTeam(participants);
        validateTeamCount(teams);
        teams.values().forEach(this::validateTeam);
        this.participants = List.copyOf(participants);
    }

    private static Map<Integer, List<ScoringParticipant>> groupByTeam(List<ScoringParticipant> participants) {
        return participants.stream()
                .collect(groupingBy(ScoringParticipant::teamId));
    }

    private void validateTeamCount(Map<Integer, List<ScoringParticipant>> teams) {
        if (teams.size() != TEAM_COUNT) {
            throw new InvalidDfScoreRequestException("참가자는 두 팀이어야 합니다: 팀 " + teams.keySet());
        }
    }

    private void validateTeam(List<ScoringParticipant> team) {
        validateTeamSize(team);
        validateKillInvolvement(team);
    }

    private void validateTeamSize(List<ScoringParticipant> team) {
        if (team.size() != TEAM_SIZE) {
            throw new InvalidDfScoreRequestException(
                    "팀 %d는 5명이어야 합니다: %d명".formatted(team.getFirst().teamId(), team.size()));
        }
    }

    private void validateKillInvolvement(List<ScoringParticipant> team) {
        int teamKills = teamKills(team);
        boolean exceeded = team.stream()
                .anyMatch(participant -> participant.killInvolvement() > teamKills);
        if (exceeded) {
            throw new InvalidDfScoreRequestException(
                    "팀 %d에 킬과 어시스트의 합이 팀 킬(%d)보다 큰 참가자가 있습니다".formatted(team.getFirst().teamId(), teamKills));
        }
    }

    private int teamKills(Collection<ScoringParticipant> team) {
        return team.stream()
                .mapToInt(ScoringParticipant::kills)
                .sum();
    }

    public boolean everyoneHasPosition() {
        return participants.stream()
                .allMatch(ScoringParticipant::hasPosition);
    }

    /** 포지션이 없는 참가자가 있으면 거짓이다. */
    public boolean eachTeamHasDistinctPositions() {
        return groupByTeam(participants).values().stream()
                .allMatch(this::hasDistinctPositions);
    }

    private boolean hasDistinctPositions(List<ScoringParticipant> team) {
        long distinctPositions = team.stream()
                .filter(ScoringParticipant::hasPosition)
                .map(ScoringParticipant::position)
                .distinct()
                .count();
        return distinctPositions == team.size();
    }

    /** 항목의 10명 평균. 포지션과 관계없이 경기 참가자 전체로 구한다. */
    public double averageOf(ScoreFeature feature) {
        return participants.stream()
                .mapToDouble(participant -> participant.statOf(feature))
                .average()
                .orElseThrow();
    }

    public TeamContribution teamContributionOf(ScoringParticipant participant) {
        return new TeamContribution(killParticipationOf(participant), participant.kda());
    }

    private double killParticipationOf(ScoringParticipant participant) {
        int teamKills = teamKillsOf(participant);
        if (teamKills == 0) {
            return 0;
        }
        return (double) participant.killInvolvement() / teamKills;
    }

    public int teamKillsOf(ScoringParticipant participant) {
        return teamKills(groupByTeam(participants).get(participant.teamId()));
    }
}
