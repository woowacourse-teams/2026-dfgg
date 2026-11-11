package dfgg.domain.aram;

import dfgg.domain.language.Language;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 한 언어의 증강 요약 설명 원문. 끼워넣기, 자리표시자, 게임 서식을 풀어 평문을 만든다.
 * <p>
 * 하나라도 풀지 못하면 만들지 않는다. 화면에 {@code @RangeIncrease@} 같은 기호나 틀린 숫자를 보여 주지 않기 위해서다.
 */
public final class AugmentDescriptionTemplate {

    /** 끼워 넣은 문자열이 또 끼워넣기를 품을 수 있다. 서로를 끼워 넣는 문자열에서 멈추려고 횟수를 제한한다. */
    private static final int MAX_INCLUDE_DEPTH = 5;
    private static final Pattern INCLUDE = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");
    /** 스킬 증강의 스킬 이름은 게임 중에 고른 스킬로 정해져 서버가 알 수 없다. */
    private static final Pattern SPELL_NAME = Pattern.compile("\\{\\{\\s*spellname\\s*}}", Pattern.CASE_INSENSITIVE);
    private static final Pattern KOREAN_SPELL_NAME = Pattern.compile("\\{\\{\\s*spellname\\s*}}( 스킬)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile("@([^@]+)@(%?)");
    private static final Pattern ICON = Pattern.compile("%i:[^%]+%");
    private static final Pattern LINE_BREAK = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");

    private final Language language;
    private final String text;

    public AugmentDescriptionTemplate(Language language, String text) {
        this.language = language;
        this.text = text;
    }

    /** @param strings 같은 언어의 게임 문자열. 키는 소문자다 */
    public Optional<String> render(Map<String, String> strings, SpellValues values) {
        return resolveIncludes(strings)
                .flatMap(resolved -> fillPlaceholders(resolved, values))
                .map(AugmentDescriptionTemplate::stripMarkup)
                .filter(AugmentDescriptionTemplate::isFullyResolved);
    }

    /** 이 원문이 끼워 넣는 게임 문자열 키(소문자). 스킬 이름 자리는 문자열로 풀지 않으므로 빠진다. */
    public Set<String> includedKeys() {
        return INCLUDE.matcher(replaceSpellName(text)).results()
                .map(AugmentDescriptionTemplate::keyOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Optional<String> resolveIncludes(Map<String, String> strings) {
        String resolved = text;
        for (int depth = 0; depth < MAX_INCLUDE_DEPTH; depth++) {
            resolved = replaceSpellName(resolved);
            List<String> keys = INCLUDE.matcher(resolved).results().map(AugmentDescriptionTemplate::keyOf).toList();
            if (keys.isEmpty()) {
                return Optional.of(resolved);
            }
            if (!strings.keySet().containsAll(keys)) {
                return Optional.empty();
            }
            resolved = INCLUDE.matcher(resolved)
                    .replaceAll(include -> Matcher.quoteReplacement(strings.get(keyOf(include))));
        }
        return Optional.of(replaceSpellName(resolved));
    }

    private String replaceSpellName(String text) {
        if (language == Language.KO_KR) {
            return KOREAN_SPELL_NAME.matcher(text).replaceAll("해당 스킬");
        }
        return SPELL_NAME.matcher(text).replaceAll("[Ability]");
    }

    private static String keyOf(MatchResult include) {
        return include.group(1).toLowerCase(Locale.ROOT);
    }

    private Optional<String> fillPlaceholders(String text, SpellValues values) {
        Map<String, String> filled = new HashMap<>();
        for (MatchResult placeholder : PLACEHOLDER.matcher(text).results().toList()) {
            Optional<String> value = fill(placeholder, values);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            filled.put(placeholder.group(), value.get());
        }
        return Optional.of(PLACEHOLDER.matcher(text)
                .replaceAll(placeholder -> Matcher.quoteReplacement(filled.get(placeholder.group()))));
    }

    /** {@code @이름@} 또는 {@code @이름*배수@}. 바로 뒤의 {@code %}는 값 표기에 넘긴다. */
    private Optional<String> fill(MatchResult placeholder, SpellValues values) {
        String[] expression = placeholder.group(1).split("\\*", 2);
        boolean percentFollows = !placeholder.group(2).isEmpty();
        if (expression.length == 1) {
            return values.format(expression[0], 1, percentFollows, language);
        }
        try {
            return values.format(expression[0], Double.parseDouble(expression[1]), percentFollows, language);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String stripMarkup(String text) {
        String withoutIcons = ICON.matcher(text).replaceAll("");
        String withLineBreaks = LINE_BREAK.matcher(withoutIcons).replaceAll("\n");
        String plain = TAG.matcher(withLineBreaks).replaceAll("");
        return plain.lines()
                .map(line -> SPACES.matcher(line).replaceAll(" ").strip())
                .collect(Collectors.joining("\n"))
                .strip();
    }

    private static boolean isFullyResolved(String description) {
        return !description.contains("@") && !description.contains("{{");
    }
}
