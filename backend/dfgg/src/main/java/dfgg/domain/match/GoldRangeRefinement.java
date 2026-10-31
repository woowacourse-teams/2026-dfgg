package dfgg.domain.match;

import java.util.Objects;
import java.util.Optional;

/**
 * 구매 직전 골드를 어느 근거까지 사용해 계산했는지 단계별로 보존한다.
 * 뒤 단계의 값이 없더라도 앞 단계의 결과는 버리지 않는다. 값이 존재하면
 * totalGold 보정은 기본 범위 안에, 자연 골드 보정은 그다음 범위 안에 있어야 한다.
 *
 * @param base 프레임 currentGold와 아이템 거래액 구간만 사용한 기본 범위
 * @param totalGoldRefinement 누적 획득 골드 차이가 거래 흐름과 양립할 때의 범위. 결측·충돌이면 비어 있다
 * @param passiveAssumption 자연 골드의 구매 전후 최소 수입 시점까지 적용한 범위. 시각·가정이 불충분하면 비어 있다
 * @param passiveAndKillAssumption 자연 골드에 본인 킬 최소 지급액까지 더한 범위. 킬 정보가 불충분하면 비어 있다
 */
public record GoldRangeRefinement(
        GoldRange base,
        Optional<GoldRange> totalGoldRefinement,
        Optional<GoldRange> passiveAssumption,
        Optional<GoldRange> passiveAndKillAssumption
) {

    public GoldRangeRefinement {
        Objects.requireNonNull(base, "기본 Gold Range가 필요합니다.");
        Objects.requireNonNull(totalGoldRefinement, "totalGold 보정 결과가 필요합니다.");
        Objects.requireNonNull(passiveAssumption, "자연 골드 보정 결과가 필요합니다.");
        Objects.requireNonNull(passiveAndKillAssumption, "자연 골드·킬 보정 결과가 필요합니다.");
        if (passiveAssumption.isEmpty() && passiveAndKillAssumption.isPresent()) {
            throw new IllegalArgumentException("자연 골드 보정 없이 킬 결합 보정만 보존할 수 없습니다.");
        }
        totalGoldRefinement.ifPresent(range -> requireContained(base, range));
        passiveAssumption.ifPresent(range -> requireContained(totalGoldRefinement.orElse(base), range));
        passiveAndKillAssumption.ifPresent(range -> requireContained(passiveAssumption.orElseThrow(), range));
    }

    private static void requireContained(GoldRange outer, GoldRange inner) {
        if (inner.lower() < outer.lower() || inner.upper() > outer.upper()) {
            throw new IllegalArgumentException("보정한 Gold Range는 이전 범위 안에 있어야 합니다.");
        }
    }
}
