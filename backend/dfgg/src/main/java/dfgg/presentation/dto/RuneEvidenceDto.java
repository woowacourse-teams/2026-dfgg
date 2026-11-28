package dfgg.presentation.dto;

/**
 * @param games    이 페이지와 같은 (핵심 룬, 보조 계열) 묶음을 쓴 판 수
 * @param pickRate 챔피언·포지션 전체 판 중 그 묶음을 쓴 비율
 */
public record RuneEvidenceDto(int games, double pickRate) {
}
