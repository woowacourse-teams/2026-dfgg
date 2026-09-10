package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import java.util.List;

/**
 * 어떤 아군을 추천 이유로 댈지 정한다 — 승률 lift를 쓴다.
 * <p>
 * {@code P(win | 나, 아군, item) / P(win | 나, item)}이다.
 * "이 아군과 함께일 때 이 아이템이 평소보다 더 잘 이기는가"를 묻는다.
 * <p>
 * 구매 lift는 상관이지 시너지가 아니다.
 * <p>
 * 승률 lift는 "그래서 이겼는가"를 물으므로 그런 상관이 걸러진다. 드래프트 때문에 함께
 * 등장할 뿐이라면 구매율은 올라도 승률까지 오를 이유가 없다.
 * <p>
 * 후보 발견은 여전히 구매 lift가 맡는다({@code scoreByChampionId}).
 * "무엇을 후보로 올릴까"와 "왜 좋은가"는 다른 질문이고, 승률은 표본이 얇아 발견에 쓰기 어렵다.
 */
public final class AllyEvidence {

    /** 넷을 다 늘어놓으면 "누구 때문인가"가 흐려진다. */
    private static final int MAXIMUM_ALLIES = 2;

    /**
     * 이 아군과 함께일 때 더 이겨야 이유가 된다. 1.0이 "평소만큼 이긴다"다.
     * <p>
     * 1.08은 대략 상위 25% 지점이다. 더 낮추면 표본 오차에 묻힌다
     */
    private static final double MINIMUM_WIN_LIFT = 1.08;

    private AllyEvidence() {
    }

    public static List<Long> championIdsFor(ItemCandidate candidate) {
        return candidate.evidenceOf(CandidateSource.ALLY_SYNERGY)
                .map(SourceEvidence::winLiftByChampionId)
                .map(winLiftByAlly -> ChampionEvidence.topChampionIds(
                        winLiftByAlly, MINIMUM_WIN_LIFT, MAXIMUM_ALLIES))
                .orElse(List.of());
    }

}
