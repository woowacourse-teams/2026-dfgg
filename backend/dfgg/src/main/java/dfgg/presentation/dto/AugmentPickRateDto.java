package dfgg.presentation.dto;

import dfgg.domain.aram.Augment;
import dfgg.domain.aram.RankedAugment;
import dfgg.domain.language.Language;
import java.util.Locale;

/** 증강 하나의 픽률. 증강 승률은 표시할 수 없어 필드를 두지 않는다. */
public record AugmentPickRateDto(
        int augmentId,
        String name,
        String rarity,
        int rank,
        double pickRate,
        String iconUrl
) {

    public static AugmentPickRateDto of(RankedAugment ranked, String iconUrl, Language language) {
        Augment augment = ranked.augment();
        return new AugmentPickRateDto(
                augment.augmentId(),
                augment.nameIn(language),
                augment.rarity().name().toLowerCase(Locale.ROOT),
                ranked.rank(),
                ranked.displayedPickRate(),
                iconUrl
        );
    }
}
