package dfgg.domain.match;

/** 룬 페이지 아래의 능력치 파편 3줄. 게임 화면 순서대로 공격, 유연, 방어 줄이다. */
public record StatShards(int offenseId, int flexId, int defenseId) {

    public StatShards {
        if (offenseId <= 0 || flexId <= 0 || defenseId <= 0) {
            throw new IllegalArgumentException("파편 ID는 양수여야 합니다.");
        }
    }
}
