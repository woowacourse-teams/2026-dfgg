package dfgg.presentation.dto.response;

import dfgg.presentation.dto.AugmentPickRateDto;
import java.util.List;

/**
 * 챔피언의 증강 픽률 목록.
 *
 * @param patch    데이터 기준 패치. 라이브 패치보다 늦을 수 있다
 * @param augments 등급별로 묶고 등급 안에서 순위 순으로 정렬한 증강
 */
public record ChampionAugmentsResponse(String patch, List<AugmentPickRateDto> augments) {
}
