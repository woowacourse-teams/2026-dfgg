package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.EnumMap;
import java.util.Map;

/**
 * 묶음별 문구표. 문장 조립과 분리해 두어 문구만 갈아끼울 수 있게 한다.
 * <p>
 * 문구는 각 generator가 실제로 재는 것에 맞춘다.
 * 특히 BUILD와 SELF_SYNERGY를 뭉뚱그리면 사실과 달라진다
 * — BUILD는 "지금까지 산 것 다음에 무엇을 사는가"이고,
 * SELF_SYNERGY는 구매 이력을 아예 보지 않고 "이 아이템이 이 챔피언에게 본질적으로 맞는가"를 본다.
 * <p>
 * {@code %s}는 내 챔피언 이름 자리다.
 */
public final class ReasonPhrases {

    private static final Map<ReasonGroup, ReasonPhrase> PHRASES = buildPhrases();

    private ReasonPhrases() {
    }

    public static ReasonPhrase of(ReasonGroup group) {
        return PHRASES.get(group);
    }

    private static Map<ReasonGroup, ReasonPhrase> buildPhrases() {
        Map<ReasonGroup, ReasonPhrase> phrases = new EnumMap<>(ReasonGroup.class);
        phrases.put(ReasonGroup.BUILD, new ReasonPhrase(
                "현재 빌드 흐름에 잘 맞고",
                "현재 빌드 흐름에 잘 맞아",
                "현재 빌드 흐름과는 조금 어긋나요"));
        phrases.put(ReasonGroup.SELF_SYNERGY, new ReasonPhrase(
                "%s의 특성과 잘 맞고",
                "%s의 특성과 잘 맞아",
                "%s의 특성과는 잘 맞지 않는 편이에요"));
        phrases.put(ReasonGroup.ALLY_SYNERGY, new ReasonPhrase(
                "아군 조합과 시너지가 좋고",
                "아군 조합과 시너지가 좋아",
                "아군 조합과의 시너지는 크지 않아요"));
        phrases.put(ReasonGroup.COUNTER, new ReasonPhrase(
                "상대 조합에 대응하기 좋고",
                "상대 조합에 대응하기 좋아",
                "상대 조합을 상대로는 이점이 크지 않아요"));
        phrases.put(ReasonGroup.PATCH_META, new ReasonPhrase(
                "현재 패치에서 효율이 좋고",
                "현재 패치에서 효율이 좋아",
                "현재 패치에서는 효율이 떨어지는 편이에요"));
        phrases.put(ReasonGroup.TEAM_COMPOSITION, new ReasonPhrase(
                "양 팀 조합에서 활용도가 높고",
                "양 팀 조합에서 활용도가 높아",
                "양 팀 조합에서는 활용도가 낮은 편이에요"));
        phrases.put(ReasonGroup.CONTEXT, new ReasonPhrase(
                "지금 상황에 알맞고",
                "지금 상황에 알맞아",
                "지금 상황에서는 우선순위가 조금 낮아요"));
        return phrases;
    }
}
