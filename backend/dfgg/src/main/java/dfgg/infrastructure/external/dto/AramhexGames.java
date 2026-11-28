package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 챔피언 또는 챔피언×증강의 판 수. 입력의 승률 필드는 일부러 읽지 않는다. 증강 승률은 표시할 수 없다.
 * <p>
 * 필드가 빠지면 기본값 0으로 채워져 픽률 0%가 조용히 저장되므로 필수로 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AramhexGames(@JsonProperty(required = true) long games) {
}
