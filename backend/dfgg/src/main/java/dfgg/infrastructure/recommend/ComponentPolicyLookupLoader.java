package dfgg.infrastructure.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.ComponentPolicyLookup;
import dfgg.domain.match.PreparedComponentPolicy;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 파일의 계약과 코드 화이트리스트·기준값을 대조하고, 정확한 바이트 지문으로 모델과 연결한다. */
public final class ComponentPolicyLookupLoader {
    public static final String SCHEMA_VERSION = "component-policy-lookup-v2";

    private ComponentPolicyLookupLoader() {}

    public static ComponentPolicyLookup load(byte[] bytes) throws IOException {
        var root = new ObjectMapper().readTree(bytes);
        var prepared = root.path("prepared");
        if (!root.path("schemaVersion").asText().equals(SCHEMA_VERSION)
                || !root.path("miningSplit").asText().equals("train")
                || !root.path("trainMatchIds").isArray() || root.path("trainMatchIds").isEmpty()
                || !preparedWhitelist(prepared.path("whitelist")).equals(new TreeMap<>(PreparedComponentPolicy.LINES))
                || prepared.path("minGames").asInt() != PreparedComponentPolicy.MIN_GAMES
                || prepared.path("minLinePurchaseRate").asDouble() != PreparedComponentPolicy.MIN_LINE_PURCHASE_RATE) {
            throw new IllegalArgumentException("정책 조회표 계약 또는 코드 화이트리스트·기준값 불일치");
        }
        var lines = new HashMap<ComponentPolicyLookup.RoleKey, ComponentPolicyLookup.Prepared>();
        for (var entry : array(prepared, "entries")) {
            put(lines, new ComponentPolicyLookup.RoleKey(entry.path("championId").asInt(), entry.path("role").asText()),
                    new ComponentPolicyLookup.Prepared(entry.path("games").asInt(), entry.path("linePurchaseRate").asDouble()));
        }
        try {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new ComponentPolicyLookup(root.path("patch").asText(), sha, lines);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Iterable<JsonNode> array(JsonNode parent, String field) {
        if (!parent.path(field).isArray()) throw new IllegalArgumentException("정책 조회표 " + field + " 누락");
        return parent.get(field);
    }

    private static <K, V> void put(Map<K, V> map, K key, V value) {
        if (map.putIfAbsent(key, value) != null) throw new IllegalArgumentException("정책 조회표 중복 키: " + key);
    }

    private static Map<Integer, List<Integer>> preparedWhitelist(JsonNode node) {
        var result = new TreeMap<Integer, List<Integer>>();
        node.properties().forEach(entry -> {
            var upgrades = new java.util.ArrayList<Integer>();
            entry.getValue().forEach(id -> upgrades.add(id.asInt()));
            result.put(Integer.parseInt(entry.getKey()), List.copyOf(upgrades));
        });
        return result;
    }
}
