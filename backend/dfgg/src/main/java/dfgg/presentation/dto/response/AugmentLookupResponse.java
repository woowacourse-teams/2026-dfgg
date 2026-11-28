package dfgg.presentation.dto.response;

import dfgg.presentation.dto.AugmentPickRateDto;
import java.util.List;

/**
 * 화면에서 읽은 후보의 픽률.
 *
 * @param patch          데이터 기준 패치. 라이브 패치보다 늦을 수 있다
 * @param augments       찾은 증강. 요청 순서를 유지한다
 * @param unmatchedNames 찾지 못한 이름. 받은 글자 그대로다
 */
public record AugmentLookupResponse(String patch, List<AugmentPickRateDto> augments, List<String> unmatchedNames) {
}
