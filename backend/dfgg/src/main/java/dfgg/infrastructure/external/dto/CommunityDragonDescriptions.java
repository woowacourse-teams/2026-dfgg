package dfgg.infrastructure.external.dto;

import dfgg.domain.aram.SpellValues;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 같은 패치의 증강 정의와 언어별 문자열. 픽률 데이터의 증강 ID로 언어별 요약 설명을 만든다. */
public final class CommunityDragonDescriptions {

    private final KiwiAugments augments;
    private final List<GameStrings> strings;

    public CommunityDragonDescriptions(KiwiAugments augments, List<GameStrings> strings) {
        this.augments = augments;
        this.strings = List.copyOf(strings);
    }

    /** @return 언어 키({@code ko-KR})별 설명. 만든 언어만 담고, 하나도 없으면 비어 있다 */
    public Map<String, String> descriptionOf(int augmentId) {
        Optional<String> key = augments.descriptionKeyOf(augmentId);
        if (key.isEmpty()) {
            return Map.of();
        }
        SpellValues values = augments.valuesOf(augmentId);
        Map<String, String> descriptions = new HashMap<>();
        for (GameStrings table : strings) {
            table.templateOf(key.get())
                    .flatMap(template -> template.render(table.asMap(), values))
                    .ifPresent(description -> descriptions.put(table.language().tag(), description));
        }
        return Map.copyOf(descriptions);
    }
}
