package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dfgg.domain.aram.AugmentRarity;
import java.util.Optional;

/** CommunityDragon {@code cherry-augments.json}의 증강 하나. 아레나 증강도 같은 파일에 있어 이름이 겹칠 수 있다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CherryAugment(
        int id,
        @JsonProperty("nameTRA") String name,
        String rarity,
        @JsonProperty("augmentSmallIconPath") String iconPath
) {

    public boolean isNamed() {
        return name != null && !name.isBlank();
    }

    /** {@code kEventChoice}처럼 칼바람 증강 등급이 아니면 비어 있다. */
    public Optional<AugmentRarity> augmentRarity() {
        return switch (String.valueOf(rarity)) {
            case "kSilver" -> Optional.of(AugmentRarity.SILVER);
            case "kGold" -> Optional.of(AugmentRarity.GOLD);
            case "kPrismatic" -> Optional.of(AugmentRarity.PRISMATIC);
            default -> Optional.empty();
        };
    }
}
