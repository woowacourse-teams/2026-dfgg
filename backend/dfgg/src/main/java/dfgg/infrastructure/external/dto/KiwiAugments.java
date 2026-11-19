package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.SpellCalculation;
import dfgg.domain.aram.SpellValues;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CommunityDragon {@code kiwi.bin.json}(칼바람 아수라장 정의)에서 증강의 요약 설명 키와 주문 값표만 남긴 것.
 * <p>
 * 파일이 13MB이고 대부분 이펙트 정의라서 항목을 하나씩 읽고 필요 없는 항목은 바로 버린다.
 */
public final class KiwiAugments {

    private final Map<Integer, KiwiAugment> augments;
    private final Map<String, KiwiSpell> spells;

    private KiwiAugments(Map<Integer, KiwiAugment> augments, Map<String, KiwiSpell> spells) {
        this.augments = augments;
        this.spells = spells;
    }

    public static KiwiAugments read(JsonParser parser) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new AugmentDataRejectedException("kiwi.bin.json이 객체가 아닙니다.");
        }
        Map<Integer, KiwiAugment> augments = new HashMap<>();
        Map<String, KiwiSpell> spells = new HashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String path = parser.currentName();
            parser.nextToken();
            JsonNode entry = parser.readValueAsTree();
            String type = entry.path("__type").asText();
            if (type.equals("AugmentData")) {
                augmentOf(entry).ifPresent(augment -> putUnique(augments, augment));
            }
            if (type.equals("SpellObject")) {
                spells.put(path, spellOf(entry.path("mSpell")));
            }
        }
        return new KiwiAugments(Map.copyOf(augments), Map.copyOf(spells));
    }

    private static KiwiSpell spellOf(JsonNode spell) {
        return new KiwiSpell(dataValuesOf(spell), KiwiCalculations.of(spell.path("mSpellCalculations")));
    }

    /** 픽률 데이터의 증강 ID는 {@code AugmentPlatformId}다. ID나 설명 키가 없으면 설명을 만들 수 없다. */
    private static Optional<KiwiAugment> augmentOf(JsonNode entry) {
        JsonNode id = entry.path("AugmentPlatformId");
        String descriptionKey = entry.path("DescriptionTra").asText("");
        if (!id.isInt() || descriptionKey.isBlank()) {
            return Optional.empty();
        }
        List<String> spellPaths = new ArrayList<>();
        spellPaths.add(entry.path("RootSpell").asText(""));
        entry.path("AdditionalSpells").forEach(spell -> spellPaths.add(spell.asText("")));
        return Optional.of(new KiwiAugment(id.intValue(), descriptionKey.toLowerCase(Locale.ROOT),
                List.copyOf(spellPaths)));
    }

    private static void putUnique(Map<Integer, KiwiAugment> augments, KiwiAugment augment) {
        if (augments.putIfAbsent(augment.id(), augment) != null) {
            throw new AugmentDataRejectedException("kiwi.bin.json 증강 ID가 겹칩니다: " + augment.id());
        }
    }

    /** 설명에 쓰는 값은 단계별 값 중 첫 번째다. 값 목록이 없으면 게임 데이터의 기본값인 0이다. */
    private static Map<String, Double> dataValuesOf(JsonNode spell) {
        Map<String, Double> values = new HashMap<>();
        for (JsonNode dataValue : spell.path("DataValues")) {
            String name = dataValue.path("name").asText("");
            if (!name.isBlank()) {
                values.put(name, dataValue.path("values").path(0).asDouble(0));
            }
        }
        return values;
    }

    public Set<String> descriptionKeys() {
        return augments.values().stream()
                .map(KiwiAugment::descriptionKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<String> descriptionKeyOf(int augmentId) {
        return Optional.ofNullable(augments.get(augmentId)).map(KiwiAugment::descriptionKey);
    }

    /** 주 주문 다음 추가 주문 순서로 합친다. 이름이 겹치면 뒤의 값을 쓴다. */
    public SpellValues valuesOf(int augmentId) {
        Map<String, Double> dataValues = new HashMap<>();
        Map<String, SpellCalculation> calculations = new HashMap<>();
        Optional.ofNullable(augments.get(augmentId))
                .ifPresent(augment -> augment.spellPaths().stream()
                        .filter(spells::containsKey)
                        .map(spells::get)
                        .forEach(spell -> {
                            dataValues.putAll(spell.dataValues());
                            calculations.putAll(spell.calculations());
                        }));
        return new SpellValues(dataValues, calculations);
    }

    private record KiwiAugment(int id, String descriptionKey, List<String> spellPaths) {
    }

    private record KiwiSpell(Map<String, Double> dataValues, Map<String, SpellCalculation> calculations) {
    }
}
