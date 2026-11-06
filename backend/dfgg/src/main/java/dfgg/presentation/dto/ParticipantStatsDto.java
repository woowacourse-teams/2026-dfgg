package dfgg.presentation.dto;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.score.ScoringParticipant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * DF 점수 요청의 참가자 한 명. 끝난 경기의 원시 값만 담는다.
 * <p>
 * 포지션은 비우거나 빈 문자열이어도 형식 오류가 아니다 — 칼바람처럼 포지션이 없는 모드가 있고, Riot 원본은 이때 {@code ""}를 준다.
 * 시야 점수와 오브젝트 피해도 칼바람에는 없어 생략할 수 있다. 협곡에서 빠지면 협곡 채점기가 거부한다.
 */
public record ParticipantStatsDto(
        @NotBlank(message = "puuid는 필수입니다")
        String puuid,

        @NotNull(message = "팀 ID는 필수입니다")
        Integer teamId,

        @Pattern(regexp = "^(TOP|JUNGLE|MID|BOTTOM|SUPPORT)?$", message = "올바른 포지션 형식이 아닙니다 (TOP, JUNGLE, MID, BOTTOM, SUPPORT 중 하나)")
        String position,

        @NotNull(message = "승리 여부는 필수입니다")
        Boolean win,

        @NotNull(message = "킬은 필수입니다")
        @PositiveOrZero(message = "킬은 0 이상이어야 합니다")
        Integer kills,

        @NotNull(message = "데스는 필수입니다")
        @PositiveOrZero(message = "데스는 0 이상이어야 합니다")
        Integer deaths,

        @NotNull(message = "어시스트는 필수입니다")
        @PositiveOrZero(message = "어시스트는 0 이상이어야 합니다")
        Integer assists,

        @NotNull(message = "챔피언에게 가한 피해는 필수입니다")
        @PositiveOrZero(message = "챔피언에게 가한 피해는 0 이상이어야 합니다")
        Integer totalDamageDealtToChampions,

        @NotNull(message = "받은 피해는 필수입니다")
        @PositiveOrZero(message = "받은 피해는 0 이상이어야 합니다")
        Integer totalDamageTaken,

        @NotNull(message = "획득 골드는 필수입니다")
        @PositiveOrZero(message = "획득 골드는 0 이상이어야 합니다")
        Integer goldEarned,

        @PositiveOrZero(message = "시야 점수는 0 이상이어야 합니다")
        Integer visionScore,

        @NotNull(message = "포탑 피해는 필수입니다")
        @PositiveOrZero(message = "포탑 피해는 0 이상이어야 합니다")
        Integer damageDealtToTurrets,

        @PositiveOrZero(message = "오브젝트 피해는 0 이상이어야 합니다")
        Integer damageDealtToObjectives
) {

    public ScoringParticipant toScoringParticipant() {
        return new ScoringParticipant(teamId, toChampionPosition(), win,
                kills, deaths, assists,
                totalDamageDealtToChampions, totalDamageTaken, goldEarned, visionScore,
                damageDealtToTurrets, damageDealtToObjectives);
    }

    /** 형식은 이미 검증됐다. 비었거나 빈 문자열이면 포지션 없음으로 넘겨 채점기가 판단하게 한다. */
    private ChampionPosition toChampionPosition() {
        if (position == null || position.isEmpty()) {
            return null;
        }
        return ChampionPosition.valueOf(position);
    }
}
