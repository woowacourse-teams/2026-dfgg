package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dfgg.domain.rune.RunePage;
import dfgg.domain.rune.StatShards;
import java.util.List;

/** Match-v5 참가자의 perks. 주 계열과 보조 계열은 배열 순서가 아니라 description으로 구분한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchPerks(List<Style> styles, StatPerks statPerks) {

    private static final String PRIMARY_STYLE = "primaryStyle";
    private static final String SUB_STYLE = "subStyle";

    public RunePage toRunePage() {
        Style primary = styleOf(PRIMARY_STYLE);
        Style sub = styleOf(SUB_STYLE);
        if (statPerks == null) {
            throw new IllegalArgumentException("Raw Match 참가자의 파편(statPerks)이 없습니다.");
        }
        return new RunePage(primary.styleId(), primary.perkIds(), sub.styleId(), sub.perkIds(),
                statPerks.toStatShards());
    }

    private Style styleOf(String description) {
        if (styles == null) {
            throw new IllegalArgumentException("Raw Match 참가자의 룬 계열(styles)이 없습니다.");
        }
        return styles.stream()
                .filter(style -> description.equals(style.description()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Raw Match 참가자에 " + description + " 계열이 없습니다."));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Style(String description, Integer style, List<Selection> selections) {

        private int styleId() {
            if (style == null) {
                throw new IllegalArgumentException(description + " 계열 ID가 없습니다.");
            }
            return style;
        }

        private List<Integer> perkIds() {
            if (selections == null) {
                throw new IllegalArgumentException(description + " 계열의 룬 선택이 없습니다.");
            }
            return selections.stream()
                    .map(Selection::perk)
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Selection(Integer perk) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatPerks(Integer offense, Integer flex, Integer defense) {

        private StatShards toStatShards() {
            if (offense == null || flex == null || defense == null) {
                throw new IllegalArgumentException("Raw Match 참가자의 파편 3줄 중 비어 있는 줄이 있습니다.");
            }
            return new StatShards(offense, flex, defense);
        }
    }
}
