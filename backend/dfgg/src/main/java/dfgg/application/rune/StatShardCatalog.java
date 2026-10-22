package dfgg.application.rune;

import dfgg.domain.rune.Rune;
import dfgg.domain.rune.RuneKind;
import java.util.Map;

/**
 * 능력치 파편 목록. Data Dragon의 {@code runesReforged.json}에는 파편이 없어서 고정 목록으로 둔다.
 * 아이콘 경로가 틀리면 동기화에서 이미지를 받지 못해 바로 실패하므로, 파편이 바뀌면 이 목록을 함께 고친다.
 */
enum StatShardCatalog {

    ADAPTIVE_FORCE(5008, "적응형 능력치", "Adaptive Force", "StatModsAdaptiveForceIcon.png"),
    ATTACK_SPEED(5005, "공격 속도", "Attack Speed", "StatModsAttackSpeedIcon.png"),
    ABILITY_HASTE(5007, "스킬 가속", "Ability Haste", "StatModsCDRScalingIcon.png"),
    MOVE_SPEED(5010, "이동 속도", "Move Speed", "StatModsMovementSpeedIcon.png"),
    HEALTH_SCALING(5001, "체력 증가 (레벨 비례)", "Health Scaling", "StatModsHealthScalingIcon.png"),
    HEALTH(5011, "체력", "Health", "StatModsHealthPlusIcon.png"),
    TENACITY_AND_SLOW_RESIST(5013, "강인함 및 둔화 저항", "Tenacity and Slow Resist", "StatModsTenacityIcon.png");

    private static final String ICON_FOLDER = "perk-images/StatMods/";

    private final int runeId;
    private final String koreanName;
    private final String englishName;
    private final String iconFile;

    StatShardCatalog(int runeId, String koreanName, String englishName, String iconFile) {
        this.runeId = runeId;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.iconFile = iconFile;
    }

    int runeId() {
        return runeId;
    }

    String iconPath() {
        return ICON_FOLDER + iconFile;
    }

    Rune toRune() {
        return new Rune(runeId, RuneKind.STAT_SHARD, Map.of("ko-KR", koreanName, "en-US", englishName));
    }
}
