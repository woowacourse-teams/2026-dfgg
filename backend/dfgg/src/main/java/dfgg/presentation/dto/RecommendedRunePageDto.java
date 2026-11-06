package dfgg.presentation.dto;

import dfgg.domain.rune.RecommendedRunePage;
import dfgg.domain.rune.RunePage;
import dfgg.domain.rune.StatShards;
import java.util.List;
import java.util.function.IntFunction;

/** 추천 룬 페이지 하나. 룬은 게임 화면 순서대로 담는다. */
public record RecommendedRunePageDto(
        int rank,
        RuneDto primaryStyle,
        RuneDto keystone,
        List<RuneDto> primaryRunes,
        RuneDto subStyle,
        List<RuneDto> subRunes,
        List<RuneDto> statShards,
        RuneEvidenceDto evidence
) {

    public RecommendedRunePageDto {
        primaryRunes = List.copyOf(primaryRunes);
        subRunes = List.copyOf(subRunes);
        statShards = List.copyOf(statShards);
    }

    public static RecommendedRunePageDto of(RecommendedRunePage recommended, IntFunction<RuneDto> rune) {
        RunePage page = recommended.page();
        List<Integer> primaryPerkIds = page.primaryPerkIds();
        StatShards shards = page.statShards();
        return new RecommendedRunePageDto(
                recommended.rank(),
                rune.apply(page.primaryStyleId()),
                rune.apply(page.keystoneId()),
                runes(primaryPerkIds.subList(1, primaryPerkIds.size()), rune),
                rune.apply(page.subStyleId()),
                runes(page.subPerkIds(), rune),
                runes(List.of(shards.offenseId(), shards.flexId(), shards.defenseId()), rune),
                new RuneEvidenceDto(recommended.pathGames(), recommended.pathPickRate())
        );
    }

    private static List<RuneDto> runes(List<Integer> runeIds, IntFunction<RuneDto> rune) {
        return runeIds.stream().map(rune::apply).toList();
    }
}
