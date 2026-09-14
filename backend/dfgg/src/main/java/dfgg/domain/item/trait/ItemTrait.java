package dfgg.domain.item.trait;

/**
 * Data Dragon 태그만으로 표현하기 어려운 아이템 효과를 보충한다.
 */
public enum ItemTrait {
    ARMOR_PENETRATION("방어력 관통"),
    MAGIC_PENETRATION("마법 관통"),
    ARMOR("방어력"),
    MAGIC_RESIST("마법 저항력"),
    CRITICAL_STRIKE("크리티컬"),
    LIFE_STEAL("생명력 흡수"),
    TENACITY("강인함"),

    // Support item traits V2
    ENGAGE(""),
    PEEL(""),
    HEAL(""),
    SHIELD(""),
    TEAM_BUFF(""),
    // Support item traits V3
    MOBILITY_BUFF("기동력 강화"),
    ATTACK_SPEED_BUFF("아군 공격속도 강화"),
    ALLY_SUSTAIN("지속 아군 회복"),

    AOE_HEAL("광역 회복"),
    CARRY_PROTECTION("핵심 아군 보호"),
    AOE_DAMAGE_DEFENSE("광역 피해 방어"),
    CC_CLEANSE("CC 해제 및 회복"),

    ATTACK_SPEED_AND_DAMAGE_BUFF("아군 평타 딜러 강화"),
    DAMAGE_REDUCTION_AND_SLOW("피해 감소 및 둔화"),
    ALLY_OFFENSE_DEFENSE_BUFF("아군 공격 및 방어 강화"),

    SKILL_POKE("스킬 기반 견제"),
    SELF_HEAL_AND_MOBILITY_BUFF("자신 회복 및 기동력 강화"),
    ATTACK_BUFF_AND_ENEMY_VULNERABILITY("공격 강화 및 적 받는 피해량 증가"),
    CC_ABILITY_HASTE_AND_ENEMY_VULNERABILITY("CC 스킬 가속 및 적 받는 피해량 증가"),

    AP_AND_ABILITY_HASTE_BUFF("주문력 및 스킬 가속 강화"),
    HEAL_SHIELD_AMPLIFY("회복 및 보호막 강화"),
    DAMAGE_TO_HEAL("피해 기반 아군 회복"),
    MANA_REGEN_BASED_HEAL_SHIELD_AMPLIFY("마나 재생 기반 회복 및 보호막 강화"),
    ;

    private final String displayName;

    ItemTrait(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
