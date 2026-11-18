package dfgg.application.recommend.v3.feature;

import dfgg.domain.item.Item;
import java.util.List;

/**
 * 후보가 지금 상황에 맞는지를 재는 짝. "후보가 이 태그를 가졌다면, 이 상황 값만큼 맞다."
 * <p>
 * 상황 값(오른쪽)은 질의 단위라 한 요청의 모든 후보에게 같다. 태그가 있는 후보에게만 그 값을 주면
 * 상황이 바뀔 때 그 후보들의 점수만 움직여 순서가 바뀐다.
 * <p>
 * 태그는 아이템 정보({@code items.tags})의 것을 그대로 쓴다. 마법 저항은 상황 값 계산과 같이
 * {@code SpellBlock}으로 본다({@code MagicResist}가 붙은 아이템은 모두 {@code SpellBlock}도 붙어 있다).
 */
public enum SituationMatch {

    SPELL_BLOCK_AGAINST_ENEMY_SPELL_DAMAGE(
            FeatureName.CANDIDATE_SPELL_BLOCK_X_ENEMY_SPELL_DAMAGE_SHARE,
            FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE, "SpellBlock"),
    SPELL_BLOCK_AGAINST_ENEMY_MAGES(
            FeatureName.CANDIDATE_SPELL_BLOCK_X_ENEMY_MAGE_KILL_SHARE,
            FeatureName.ENEMY_MAGE_KILL_SHARE, "SpellBlock"),
    ARMOR_AGAINST_ENEMY_MARKSMEN(
            FeatureName.CANDIDATE_ARMOR_X_ENEMY_MARKSMAN_KILL_SHARE,
            FeatureName.ENEMY_MARKSMAN_KILL_SHARE, "Armor"),
    ARMOR_AGAINST_ENEMY_ASSASSINS(
            FeatureName.CANDIDATE_ARMOR_X_ENEMY_ASSASSIN_KILL_SHARE,
            FeatureName.ENEMY_ASSASSIN_KILL_SHARE, "Armor"),
    MAGIC_PEN_AGAINST_ENEMY_SPELL_BLOCK(
            FeatureName.CANDIDATE_MAGIC_PEN_X_ENEMY_SPELL_BLOCK_SHARE,
            FeatureName.ENEMY_SPELL_BLOCK_GOLD_SHARE, "MagicPenetration"),
    ARMOR_PEN_AGAINST_ENEMY_ARMOR(
            FeatureName.CANDIDATE_ARMOR_PEN_X_ENEMY_ARMOR_SHARE,
            FeatureName.ENEMY_ARMOR_GOLD_SHARE, "ArmorPenetration"),
    DEFENSIVE_BY_MY_LEVEL_LEAD(
            FeatureName.CANDIDATE_DEFENSIVE_X_MY_LEVEL_LEAD,
            FeatureName.MY_LEVEL_LEAD, "Armor", "SpellBlock"),
    ;

    private final FeatureName feature;
    private final FeatureName situation;
    private final List<String> tags;

    SituationMatch(FeatureName feature, FeatureName situation, String... tags) {
        this.feature = feature;
        this.situation = situation;
        this.tags = List.of(tags);
    }

    /** 이 짝의 값을 쓰는 칸. */
    public FeatureName feature() {
        return feature;
    }

    /** 곱할 상황 값이 있는 칸. */
    public FeatureName situation() {
        return situation;
    }

    /** 태그 중 하나라도 가지면 맞는 후보다. 둘 다 가져도 한 번만 맞는다. */
    public boolean matches(Item candidate) {
        return tags.stream().anyMatch(candidate::hasTag);
    }
}
