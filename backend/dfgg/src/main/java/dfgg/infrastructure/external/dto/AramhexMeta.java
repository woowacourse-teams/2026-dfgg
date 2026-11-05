package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dfgg.common.exception.AugmentDataRejectedException;

/** 패치 데이터의 설명 파일. 라이선스가 바뀌면 이용 조건을 다시 봐야 하므로 받지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AramhexMeta(String license) {

    private static final String EXPECTED_LICENSE = "CC BY-NC 4.0";

    public void validate() {
        if (!EXPECTED_LICENSE.equals(license)) {
            throw new AugmentDataRejectedException("aramhex-data 라이선스가 바뀌었습니다: " + license);
        }
    }
}
