package dfgg.presentation.dto;

import java.util.List;

/**
 * 이 아이템을 왜 추천했는지를 설명한다. 문장으로 내보내지 않는다.
 * <p>
 *
 * @param counter 이 적들 때문에 가치가 올랐다. 모델이 counter 근거를 실제로 썼을 때만 채운다
 * @param ally    이 아군들과 맞물린다
 * @param traits  아이템 자체의 성질. 모델 판단이 아니라 사실이므로 항상 낸다
 */
public record RecommendationDescription(
        List<ChampionRefDto> counter,
        List<ChampionRefDto> ally,
        List<String> traits
) {
    public RecommendationDescription {
        counter = List.copyOf(counter);
        ally = List.copyOf(ally);
        traits = List.copyOf(traits);
    }
}
