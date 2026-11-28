package dfgg.infrastructure.external.dto;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.ChampionAugment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 파일 검증을 통과한 한 패치의 판 수. 판 수 자체의 검증은 {@link #toChampionAugments()}가 한다.
 *
 * @param provisionalAugmentIds 테스트 서버 판 수가 들어 있어 챔피언 판 수로 나눌 수 없는 증강
 * @param heroes                챔피언 ID별 판 수
 * @param heroAugments          챔피언 ID, 증강 ID별 판 수
 */
public record AramhexPatchData(
        String patch,
        Set<Integer> provisionalAugmentIds,
        Map<Integer, AramhexGames> heroes,
        Map<Integer, Map<Integer, AramhexGames>> heroAugments
) {

    public Set<Integer> augmentIds() {
        Stream<Integer> withPickRows = heroAugments.values().stream()
                .flatMap(augments -> augments.keySet().stream());
        return Stream.concat(withPickRows, provisionalAugmentIds.stream())
                .collect(Collectors.toSet());
    }

    /** 임시 증강은 분모가 달라 픽률이 틀리게 나오므로 만들지 않는다. 증강 자체는 {@link #augmentIds()}에 남는다. */
    public List<ChampionAugment> toChampionAugments() {
        heroes.forEach(AramhexPatchData::rejectInvalidChampionGames);
        List<ChampionAugment> championAugments = new ArrayList<>();
        heroAugments.forEach((championId, augments) -> {
            long championGames = championGamesOf(championId);
            confirmedOf(augments).forEach((augmentId, games) -> championAugments.add(
                    championAugmentOf(championId, augmentId, games.games(), championGames)));
        });
        if (championAugments.isEmpty()) {
            throw new AugmentDataRejectedException("저장할 픽률이 없습니다.");
        }
        return List.copyOf(championAugments);
    }

    private static void rejectInvalidChampionGames(int championId, AramhexGames games) {
        if (games.games() <= 0) {
            throw new AugmentDataRejectedException("aramhex-data 챔피언 판 수가 올바르지 않습니다: " + championId);
        }
    }

    private Map<Integer, AramhexGames> confirmedOf(Map<Integer, AramhexGames> augments) {
        return augments.entrySet().stream()
                .filter(augment -> !provisionalAugmentIds.contains(augment.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
