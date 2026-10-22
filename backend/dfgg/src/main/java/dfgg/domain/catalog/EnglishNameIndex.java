package dfgg.domain.catalog;

import dfgg.domain.rune.Rune;
import dfgg.domain.spell.SummonerSpell;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 영어 이름으로 카탈로그 id를 찾는다 (Leaguepedia 변환용). 대소문자·공백·구두점은 무시한다 ({@code Jack Of All Trades}).
 */
public final class EnglishNameIndex {

    private static final String ENGLISH = "en-US";

    private final Map<String, Integer> idsByName;

    private EnglishNameIndex(Map<String, Integer> idsByName) {
        this.idsByName = Map.copyOf(idsByName);
    }

    public static EnglishNameIndex ofRunes(Collection<Rune> runes) {
        return of(runes, Rune::getRuneId, Rune::getName);
    }

    public static EnglishNameIndex ofSpells(Collection<SummonerSpell> spells) {
        return of(spells, SummonerSpell::getSpellId, SummonerSpell::getName);
    }

    private static <T> EnglishNameIndex of(Collection<T> entries, Function<T, Integer> idOf,
                                           Function<T, Map<String, String>> namesOf) {
        Map<String, Integer> idsByName = new HashMap<>();
        for (T entry : entries) {
            Integer id = idOf.apply(entry);
            String name = normalize(namesOf.apply(entry).get(ENGLISH));
            Integer previous = idsByName.putIfAbsent(name, id);
            if (previous != null) {
                throw new IllegalStateException("[Error] 영어 이름이 겹칩니다: " + previous + ", " + id);
            }
        }
        return new EnglishNameIndex(idsByName);
    }

    public Optional<Integer> idOf(String englishName) {
        if (englishName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(idsByName.get(normalize(englishName)));
    }

    private static String normalize(String englishName) {
        return englishName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
