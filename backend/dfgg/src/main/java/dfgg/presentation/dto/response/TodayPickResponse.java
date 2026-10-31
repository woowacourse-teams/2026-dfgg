package dfgg.presentation.dto.response;

import dfgg.domain.champion.ChampionPosition;
import java.time.LocalDate;
import java.util.List;

/**
 * 오늘의 추천 픽 응답. 대회 픽 데이터가 없으면 {@code tournament}는 null이고 {@code picks}는 빈 목록이다.
 */
public record TodayPickResponse(TournamentResponse tournament, ChampionPosition position, List<PickResponse> picks) {

    public TodayPickResponse {
        picks = List.copyOf(picks);
    }

    public static TodayPickResponse empty(ChampionPosition position) {
        return new TodayPickResponse(null, position, List.of());
    }

    public record TournamentResponse(String name, String league, LocalDate startDate) {
    }

    public record PickResponse(
            PlayerResponse player,
            LocalDate playedAt,
            Integer setNumber,
            ChampionResponse champion,
            List<EntryResponse> spells,
            List<EntryResponse> items,
            RunesResponse runes
    ) {

        public PickResponse {
            spells = List.copyOf(spells);
            items = List.copyOf(items);
        }
    }

    /** 팀은 그 경기 당시 팀이다. 선수 사진은 위키 이미지 URL이고 없을 수 있다. */
    public record PlayerResponse(String name, String team, String imageUrl) {
    }

    public record ChampionResponse(Long id, String name, String imageUrl, ChampionPosition position) {
    }

    /** 소환사 주문·아이템·룬 하나. */
    public record EntryResponse(Long id, String name, String imageUrl) {
    }

    public record TreeResponse(Long id, String name) {
    }

    /** 룬 칸이 없으면 트리·핵심은 null이다. */
    public record RunesResponse(
            TreeResponse primaryTree,
            EntryResponse keystone,
            List<EntryResponse> primary,
            TreeResponse secondaryTree,
            List<EntryResponse> secondary,
            List<EntryResponse> shards
    ) {

        public RunesResponse {
            primary = List.copyOf(primary);
            secondary = List.copyOf(secondary);
            shards = List.copyOf(shards);
        }
    }
}
