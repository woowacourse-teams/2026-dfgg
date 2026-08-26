package dfgg.domain.champion;

import java.util.Locale;

public enum ChampionTag {
    TANK,
    FIGHTER,
    MAGE,
    ASSASSIN,
    MARKSMAN,
    SUPPORT,
    ;

    public static ChampionTag from(String value) {
        return ChampionTag.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
