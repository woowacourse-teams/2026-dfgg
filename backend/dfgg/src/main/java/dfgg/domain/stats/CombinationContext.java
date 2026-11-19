package dfgg.domain.stats;

import dfgg.domain.team.Team;

public record CombinationContext(
        boolean enemyTankHeavy,
        boolean enemyApHeavy,
        boolean enemyAssassinHeavy,
        boolean allyHasMarksman,
        boolean allyTankHeavy
) {

    /**
     * 양 팀의 챔피언 구성을 분석해 통계 검색에 사용하는 다섯 가지 조건으로 변환한다.
     */
    public static CombinationContext analyze(Team enemies, Team allies) {
        return new CombinationContext(
                enemies.isTankHeavy(),
                enemies.isApHeavy(),
                enemies.isAssassinHeavy(),
                allies.hasMarksman(),
                allies.isTankHeavy()
        );
    }
}
