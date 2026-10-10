package dfgg.infrastructure.recommend;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.PreparationLookup;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;

/** 파일의 계약을 검증하고 정확한 바이트 지문으로 모델과 연결한다. */
public final class PreparationLookupLoader {
    private PreparationLookupLoader() {}
    public static PreparationLookup load(byte[] bytes) throws IOException {
        var root = new ObjectMapper().readTree(bytes);
        if (!root.path("schemaVersion").asText().equals("component-preparation-v2")
                || !root.path("miningSplit").asText().equals("train")
                || root.path("minPatternCount").asInt() != 50
                || root.path("minFutureRecipeLinkRate").asDouble() != .70
                || root.path("maxCandidatesPerKey").asInt() != 3
                || !root.path("entries").isArray() || !root.path("trainMatchIds").isArray()
                || root.path("trainMatchIds").isEmpty()
                || !root.path("splitManifestSha256").asText().matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Preparation Lookup v2 계약 불일치");
        }
        var entries = new HashMap<PreparationLookup.Key, java.util.List<PreparationLookup.Pattern>>();
        for (var entry : root.get("entries")) {
            var key = new PreparationLookup.Key(entry.path("championId").asInt(), entry.path("role").asText(), entry.path("targetItemId").asInt());
            var items = new ArrayList<PreparationLookup.Pattern>();
            if (!entry.path("items").isArray()) throw new IllegalArgumentException("Preparation items 누락");
            for (var item : entry.get("items")) {
                var pattern = new PreparationLookup.Pattern(item.path("itemId").asInt(), item.path("eligibleCount").asInt(),
                        item.path("futureRecipeLinkRate").asDouble(), item.path("eligibleQueryCount").asInt(),
                        item.path("futureRecipeLinkedCount").asInt(-1));
                if (!item.path("observationRate").isNumber()
                        || Math.abs(item.path("observationRate").asDouble() - pattern.observationRate()) > 1e-12) {
                    throw new IllegalArgumentException("Preparation observation rate 불일치");
                }
                items.add(pattern);
            }
            if (entries.putIfAbsent(key, items) != null) throw new IllegalArgumentException("Preparation 중복 키");
        }
        try {
            return new PreparationLookup(root.path("patch").asText(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), entries);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
