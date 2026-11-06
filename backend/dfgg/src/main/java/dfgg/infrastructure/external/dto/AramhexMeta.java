package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dfgg.common.exception.AugmentDataRejectedException;
import java.util.Map;
import java.util.Set;

/**
 * 패치 데이터의 설명 파일. 라이선스가 바뀌면 이용 조건을 다시 봐야 하므로 받지 않는다.
 *
 * @param provisionalAugments 정식 서버 기록이 없어 테스트 서버 판 수를 임시로 쓰는 증강
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AramhexMeta(
        String license,
        @JsonProperty("schema_version") int schemaVersion,
        String mode,
        String patch,
        int heroes,
        @JsonProperty("provisional_augments") Map<Integer, AramhexGames> provisionalAugments
) {

    private static final String EXPECTED_LICENSE = "CC BY-NC 4.0";

    public AramhexMeta {
        provisionalAugments = provisionalAugments == null ? Map.of() : Map.copyOf(provisionalAugments);
    }

    public Set<Integer> provisionalAugmentIds() {
        return provisionalAugments.keySet();
    }

    /** 임시 표시를 놓치면 테스트 서버 판 수가 픽률로 저장되므로 임시 증강 수까지 두 파일이 같은지 확인한다. */
    public void validate(AramhexLatest latest) {
        if (!EXPECTED_LICENSE.equals(license)) {
            throw new AugmentDataRejectedException("aramhex-data 라이선스가 바뀌었습니다: " + license);
        }
        if (schemaVersion != latest.schemaVersion()) {
            throw new AugmentDataRejectedException("meta.json의 schema_version이 latest.json과 다릅니다.");
        }
        if (!latest.mode().equals(mode)) {
            throw new AugmentDataRejectedException("meta.json의 mode가 latest.json과 다릅니다.");
        }
        if (!latest.patch().equals(patch)) {
            throw new AugmentDataRejectedException("meta.json의 패치가 latest.json과 다릅니다.");
        }
        if (provisionalAugments.size() != latest.provisionalAugmentCount()) {
            throw new AugmentDataRejectedException("meta.json의 임시 증강 수가 latest.json과 다릅니다.");
        }
    }

    public void validateChampionCount(int championCount) {
        if (championCount != heroes) {
            throw new AugmentDataRejectedException(
                    "aramhex-data 챔피언 수가 meta.json과 다릅니다: " + championCount + " != " + heroes);
        }
    }
}
