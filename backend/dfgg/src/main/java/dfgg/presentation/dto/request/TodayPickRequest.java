package dfgg.presentation.dto.request;

import dfgg.domain.champion.ChampionPosition;
import jakarta.validation.constraints.NotNull;

/**
 * 오늘의 추천 픽 요청. 밴은 받지 않는다 — 예전 클라이언트가 {@code bans}를 보내도 무시한다.
 */
public record TodayPickRequest(@NotNull ChampionPosition position) {
}
