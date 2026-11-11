package dfgg.presentation.dto;

import dfgg.domain.aram.Augment;
import dfgg.domain.aram.RankedAugment;
import dfgg.domain.language.Language;
import java.util.Locale;

/**
 * 증강 하나의 픽률. 증강 승률은 표시할 수 없어 필드를 두지 않는다.
 *
 * @param rank     같은 등급 안의 순위. 이 챔피언의 값이 없으면 null이다
 * @param pickRate    이 챔피언의 픽률(%). 이 챔피언의 값이 없으면 null이다
 * @param description 요청 언어의 요약 설명(평문, 줄바꿈은 {@code \n}). 수치를 채우지 못한 증강은 null이다
 */
public record AugmentPickRateDto(
        int augmentId,
        String name,
        String rarity,
        Integer rank,
        Double pickRate,
        String iconUrl,
        String description
) {

    public static AugmentPickRateDto of(RankedAugment ranked, String iconUrl, Language language) {
        return of(ranked.augment(), ranked.rank(), ranked.displayedPickRate(), iconUrl, language);
    }

    public static AugmentPickRateDto unranked(Augment augment, String iconUrl, Language language) {
        return of(augment, null, null, iconUrl, language);
    }

    private static AugmentPickRateDto of(Augment augment, Integer rank, Double pickRate, String iconUrl,
                                         Language language) {
        return new AugmentPickRateDto(
                augment.augmentId(),
                augment.nameIn(language),
                augment.rarity().name().toLowerCase(Locale.ROOT),
                rank,
                pickRate,
                iconUrl,
                augment.descriptionIn(language).orElse(null)
        );
    }
}
