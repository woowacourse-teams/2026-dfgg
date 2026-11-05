package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dfgg.common.exception.AugmentDataRejectedException;

/** 패치 데이터의 설명 파일. 라이선스가 바뀌면 이용 조건을 다시 봐야 하므로 받지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AramhexMeta(
        String license,
        @JsonProperty("schema_version") int schemaVersion,
        String mode,
        String patch,
        int heroes
) {

    private static final String EXPECTED_LICENSE = "CC BY-NC 4.0";

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
    }

    public void validateChampionCount(int championCount) {
        if (championCount != heroes) {
            throw new AugmentDataRejectedException(
                    "aramhex-data 챔피언 수가 meta.json과 다릅니다: " + championCount + " != " + heroes);
        }
    }
}
