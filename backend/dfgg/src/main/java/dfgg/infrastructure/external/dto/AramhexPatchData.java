package dfgg.infrastructure.external.dto;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.ChampionAugment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 검증을 마친 한 패치의 판 수.
 *
 * @param heroes       챔피언 ID별 판 수
 * @param heroAugments 챔피언 ID, 증강 ID별 판 수
 */
public record AramhexPatchData(
        String patch,
        Map<Integer, AramhexGames> heroes,
        Map<Integer, Map<Integer, AramhexGames>> heroAugments
) {

    public List<ChampionAugment> toChampionAugments() {
        List<ChampionAugment> championAugments = new ArrayList<>();
        heroAugments.forEach((championId, augments) -> {
            long championGames = championGamesOf(championId);
            augments.forEach((augmentId, games) -> championAugments.add(
                    championAugmentOf(championId, augmentId, games.games(), championGames)));
        });
        return List.copyOf(championAugments);
    }

    private ChampionAugment championAugmentOf(int championId, int augmentId, long games, long championGames) {
        try {
            return ChampionAugment.fromGames(patch, championId, augmentId, games, championGames);
        } catch (IllegalArgumentException exception) {
            throw new AugmentDataRejectedException(
                    "aramhex-data 판 수가 올바르지 않습니다: 챔피언 " + championId + ", 증강 " + augmentId, exception);
        }
    }

    private long championGamesOf(int championId) {
        AramhexGames games = heroes.get(championId);
        if (games == null) {
            throw new AugmentDataRejectedException("aramhex-data에 챔피언 판 수가 없습니다: " + championId);
        }
        return games.games();
    }
}
