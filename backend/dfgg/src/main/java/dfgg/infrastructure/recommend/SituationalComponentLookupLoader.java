package dfgg.infrastructure.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.SituationalComponentLookup;
import dfgg.domain.match.SituationalComponentPolicy;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 파일 계약과 코드 화이트리스트·하한을 대조한다. 기준값 조정은 설정(SituationalThresholds)에서 한다. */
public final class SituationalComponentLookupLoader {
    public static final String SCHEMA_VERSION = "situational-component-lookup-v1";

    private SituationalComponentLookupLoader() {}

    public static SituationalComponentLookup load(byte[] bytes) throws IOException {
        var root = new ObjectMapper().readTree(bytes);
        if (!root.path("schemaVersion").asText().equals(SCHEMA_VERSION)
                || !List.of("train", "all").contains(root.path("miningSplit").asText())
                || !whitelist(root.path("whitelist")).equals(new TreeMap<>(SituationalComponentPolicy.ITEMS))
                || root.path("minSupport").asInt() != SituationalComponentPolicy.MIN_SUPPORT
                || root.path("minGames").asInt() != SituationalComponentPolicy.MIN_GAMES
                || root.path("minLift").asDouble() != SituationalComponentPolicy.LIFT_FLOOR
                || root.path("minBaseRate").asDouble() != SituationalComponentPolicy.BASE_RATE_FLOOR) {
            throw new IllegalArgumentException("Situational 조회표 계약 또는 코드 화이트리스트·하한 불일치");
        }
        var lifts = new HashMap<SituationalComponentLookup.LiftKey, SituationalComponentLookup.Lift>();
        for (var entry : array(root, "lifts")) {
            put(lifts, new SituationalComponentLookup.LiftKey(entry.path("itemId").asInt(), entry.path("enemyChampionId").asInt(),
                    entry.path("role").asText()), new SituationalComponentLookup.Lift(entry.path("support").asInt(), entry.path("lift").asDouble()));
        }
        var baseRates = new HashMap<SituationalComponentLookup.BaseKey, Double>();
        for (var entry : array(root, "baseRates")) {
            double rate = entry.path("baseRate").asDouble();
            if (entry.path("games").asInt() < SituationalComponentPolicy.MIN_GAMES
                    || !(rate >= SituationalComponentPolicy.BASE_RATE_FLOOR) || rate > 1) {
                throw new IllegalArgumentException("Situational 기본 구매율 하한 불충족");
            }
            put(baseRates, new SituationalComponentLookup.BaseKey(entry.path("championId").asInt(), entry.path("role").asText(),
                    entry.path("itemId").asInt()), rate);
        }
        return new SituationalComponentLookup(root.path("patch").asText(), lifts, baseRates);
    }

    private static Iterable<JsonNode> array(JsonNode parent, String field) {
        if (!parent.path(field).isArray()) throw new IllegalArgumentException("Situational 조회표 " + field + " 누락");
        return parent.get(field);
    }

    private static <K, V> void put(Map<K, V> map, K key, V value) {
        if (map.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Situational 조회표 중복 키: " + key);
    }

    private static Map<Integer, SituationalComponentPolicy.Effect> whitelist(JsonNode node) {
        var result = new TreeMap<Integer, SituationalComponentPolicy.Effect>();
        node.properties().forEach(entry ->
                result.put(Integer.parseInt(entry.getKey()), SituationalComponentPolicy.Effect.valueOf(entry.getValue().asText())));
        return result;
    }
}
