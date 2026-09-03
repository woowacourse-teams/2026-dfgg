package dfgg.application.recommend.v3.explanation;

import java.util.List;

/**
 * 고른 이유를 한 문장으로 옮긴다.
 * <p>
 * SHAP 값 자체는 사용자에게 보여줄 것이 아니다.
 * {@code COUNTER=+1.0247}은 "counter feature들이 점수를 올렸다"까지만 말한다.
 * 여기서는 그것을 읽을 수 있는 말로 바꾸고, 값은 응답에 싣지 않는다.
 */
public class DescriptionComposer {

    private static final String ENDING = " 추천했어요.";

    /** 말할 이유가 하나도 없을 때. 설명 없는 추천을 내보내는 것보다 낫다. */
    private static final String WITHOUT_REASON = "여러 지표를 종합해" + ENDING;

    public String compose(SelectedReasons reasons, String myChampionName) {
        return sentence(reasons.highlights(), myChampionName)
                + reasons.caveat()
                .map(weight -> " 다만 " + fill(
                        ReasonPhrases.of(weight.group()).caveat(), myChampionName) + ".")
                .orElse("");
    }

    private String sentence(List<GroupWeight> highlights, String myChampionName) {
        if (highlights.isEmpty()) {
            return WITHOUT_REASON;
        }
        StringBuilder sentence = new StringBuilder();
        for (int index = 0; index < highlights.size(); index++) {
            ReasonPhrase phrase = ReasonPhrases.of(highlights.get(index).group());
            boolean last = index == highlights.size() - 1;
            if (index > 0) {
                sentence.append(' ');
            }
            sentence.append(fill(last ? phrase.terminal() : phrase.connective(), myChampionName));
        }
        return sentence + ENDING;
    }

    private String fill(String phrase, String myChampionName) {
        return phrase.formatted(myChampionName);
    }
}
