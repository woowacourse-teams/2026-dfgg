package dfgg.infrastructure.external.dto;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.Augment;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 같은 패치의 한글·영어 증강 데이터. 픽률 데이터의 숫자 ID로 이름과 등급, 아이콘을 찾는다. */
public final class CommunityDragonAugments {

    private final Map<Integer, CherryAugment> koreanById;
    private final Map<Integer, CherryAugment> englishById;

    private CommunityDragonAugments(Map<Integer, CherryAugment> koreanById, Map<Integer, CherryAugment> englishById) {
        this.koreanById = koreanById;
        this.englishById = englishById;
    }

    public static CommunityDragonAugments of(List<CherryAugment> korean, List<CherryAugment> english) {
        return new CommunityDragonAugments(byId(korean), byId(english));
    }

    private static Map<Integer, CherryAugment> byId(List<CherryAugment> augments) {
        return augments.stream()
                .collect(Collectors.toMap(CherryAugment::id, Function.identity(), (left, right) -> {
                    throw new AugmentDataRejectedException("CommunityDragon 증강 ID가 겹칩니다: " + left.id());
                }));
    }

    /** 두 언어의 이름과 칼바람 등급이 모두 있어야 연결한다. */
    public Optional<Augment> toAugment(String patch, int augmentId) {
        CherryAugment korean = koreanById.get(augmentId);
        CherryAugment english = englishById.get(augmentId);
        if (korean == null || english == null || !korean.isNamed() || !english.isNamed()) {
            return Optional.empty();
        }
        Map<String, String> name = Map.of("ko-KR", korean.name(), "en-US", english.name());
        return korean.augmentRarity().map(rarity -> new Augment(patch, augmentId, name, rarity));
    }

    public String iconPathOf(int augmentId) {
        return englishById.get(augmentId).iconPath();
    }
}
