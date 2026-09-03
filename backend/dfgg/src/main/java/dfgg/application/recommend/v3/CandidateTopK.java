package dfgg.application.recommend.v3;

/**
 * generator별 후보 수 상한. 값은 <b>Recall@K 실측</b>으로 정한다(T8, `tasks/eval-recall.md`).
 *
 * <p>이건 "몇 개를 볼 것인가"이지 "얼마나 믿을 것인가"가 아니다. 후자를 여기 넣으면
 * 이번 작업이 없애려는 수동 가중치가 된다 — 근거들 사이의 trade-off는 LTR이 학습한다.
 *
 * <p>실측 근거: 아이템 카탈로그가 159종이고 챔피언·포지션당 실제 구매 아이템은 평균 26.8종이라
 * K를 50~100으로 잡으면 그 챔피언이 산 아이템을 전부 반환해 recall이 자명하게 100%가 된다.
 * Union Recall은 K=20에서 이미 100%에 도달한다.
 */
public record CandidateTopK(int build, int selfSynergy, int allySynergy, int counter) {

    public CandidateTopK {
        validatePositive(build, "build");
        validatePositive(selfSynergy, "self-synergy");
        validatePositive(allySynergy, "ally-synergy");
        validatePositive(counter, "counter");
    }

    public int of(CandidateSource source) {
        return switch (source) {
            case BUILD -> build;
            case SELF_SYNERGY -> selfSynergy;
            case ALLY_SYNERGY -> allySynergy;
            case COUNTER -> counter;
        };
    }

    private static void validatePositive(int topK, String name) {
        if (topK < 1) {
            throw new IllegalArgumentException(name + " 후보 수는 1 이상이어야 합니다: " + topK);
        }
    }
}
