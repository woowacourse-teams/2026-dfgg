package dfgg.domain.item.trait;

import java.util.Map;

/**
 * Data Dragon 태그만으로 표현하기 어려운 아이템 효과를 보충한다.
 */
public enum ItemTrait {
    // Support item traits V2
    ENGAGE("", ""),
    PEEL("", ""),
    HEAL("", ""),
    SHIELD("", ""),
    TEAM_BUFF("", ""),
    // Support item traits V3
    SHURELYA_BATTLESONG("기동력 강화", "Mobility Boost"),
    BANDLEPIPES("아군 공격속도 강화", "Ally Attack Speed Boost"),
    WHISPERING_CIRCLET("지속 아군 회복", "Sustained Ally Healing"),

    REDEMPTION("광역 회복", "Area Healing"),
    KNIGHT_VOW("핵심 아군 보호", "Carry Protection"),
    LOCKET_OF_THE_IRON_SOLARI("광역 피해 방어", "Area Damage Protection"),
    MIKAEL_BLESSING("CC 해제 및 회복", "CC Cleanse & Heal"),

    ARDENT_CENSER("아군 평타 딜러 강화", "Basic Attack Carry Boost"),
    CELESTIAL_OPPOSITION("피해 감소 및 둔화", "Damage Reduction & Slow"),
    DREAM_MAKER("아군 공격 및 방어 강화", "Ally Offense & Defense Boost"),

    ZAZZAK_REALMSPIKE("스킬 기반 견제", "Ability-Based Poke"),
    SOLSTICE_SLEIGH("자신 회복 및 기동력 강화", "Self Heal & Mobility Boost"),
    BLOODSONG("공격 강화 및 적 받는 피해량 증가", "Attack Boost & Increased Damage Taken by Enemies"),
    IMPERIAL_MANDATE("CC 스킬 가속 및 적 받는 피해량 증가", "CC Ability Haste & Increased Damage Taken by Enemies"),

    STAFF_OF_FLOWING_WATER("주문력 및 스킬 가속 강화", "Ability Power & Ability Haste Boost"),
    MOONSTONE_RENEWER("회복 및 보호막 강화", "Heal & Shield Boost"),
    ECHOES_OF_HELIA("피해 기반 아군 회복", "Damage-Based Ally Healing"),
    DAWNCORE("마나 재생 기반 회복 및 보호막 강화", "Mana Regen-Based Heal & Shield Boost"),

    // Tank item traits
    UNENDING_DESPAIR("전투 중 지속 체력 회복", "Sustained Healing in Combat"), // 끝없는 절망
    KAENIC_ROOKERN("마법 피해 보호막", "Magic Damage Shield"), // 케이닉 루컨
    PROTOPLASM_HARNESS("체력 적을 때 폭발적 자가 체력 회복", "Burst Self-Heal at Low Health"), // 원형질 안전벨트
    ZEKE_CONVERGENCE("궁극기 연계 피해 및 둔화", "Ultimate-Triggered Damage & Slow"), // 지크의 융합
    SPIRIT_VISAGE("받는 회복 및 보호막 강화", "Increased Healing & Shielding Received"), // 정령의 형상
    SUNFIRE_AEGIS("주변 지속 피해", "Nearby Damage Over Time"), // 태양불꽃 방패
    THORNMAIL("회복 감소/피해 반사", "Healing Reduction/Damage Reflection"), // 가시 갑옷
    WARMOG_ARMOR("전투 외 체력 회복", "Out-of-Combat Health Regen"), // 워모그의 갑옷
    HEARTSTEEL("최대 체력 강화", "Max Health Boost"), // 강철심장
    FROZEN_HEART("주변 적 공격 속도 감소", "Nearby Enemy Attack Speed Reduction"), // 얼어붙은 심장
    WINTER_APPROACH("CC 연계 보호막", "CC-Triggered Shield"), // 혹한의 손길
    RANDUIN_OMEN("치명타 피해 방어", "Critical Strike Damage Reduction"), // 란두인의 예언
    DEAD_MAN_PLATE("이동 속도 증가/둔화 효과 감소", "Movement Speed Boost/Slow Resistance"), // 망자의 갑옷
    FORCE_OF_NATURE("마법 피해 방어 및 이동 속도 강화", "Magic Damage Reduction & Movement Speed Boost"), // 대자연의 힘
    ICEBORN_GAUNTLET("스킬 연계 평타 강화/둔화 영역 생성", "Ability-Empowered Attacks/Slowing Zone"), // 얼어붙은 건틀릿
    HOLLOW_RADIANCE("주변 지속 피해", "Nearby Damage Over Time"), // 공허한 광휘
    JAKSHO_THE_PROTEAN("전투 중 방어력·마법 저항력 강화", "Armor & Magic Resist Boost in Combat"), // 해신 작쇼
    ABYSSAL_MASK("주변 적 받는 마법 피해 증가", "Increased Magic Damage Taken by Nearby Enemies"), // 심연의 가면

    // Fighter item traits
    OVERLORD_BLOODMAIL("체력 비례 공격력 전환", "Health-to-Attack Damage Conversion"), // 지배자의 피갑옷
    ENDLESS_HUNGER("처치 시 흡혈 강화", "Omnivamp Boost on Takedown"), // 끝없는 갈망
    MANAMUNE("마나 기반 공격력 전환", "Mana-to-Attack Damage Conversion"), // 마나무네
    GUARDIAN_ANGEL("사망 시 부활", "Revive on Death"), // 수호 천사
    STERAK_GAGE("저체력 시 보호막", "Shield at Low Health"), // 스테락의 도전
    BLACK_CLEAVER("방어력 중첩 감소", "Stacking Armor Reduction"), // 칠흑의 양날 도끼
    EXPERIMENTAL_HEXPLATE("궁극기 사용 시 강화", "Boost After Casting Ultimate"), // 실험적 마공학판
    RAVENOUS_HYDRA("광역 공격 및 흡혈", "Area Attacks & Life Steal"), // 굶주린 히드라
    TRINITY_FORCE("스킬 후 평타 강화", "Empowered Attack After Ability"), // 삼위일체
    WIT_END("평타 시 마법피해 추가", "Bonus Magic Damage on Attack"), // 마법사의 최후
    BLADE_OF_THE_RUINED_KING("현재 체력 비례 피해", "Current Health-Based Damage"), // 몰락한 왕의 검
    MAW_OF_MALMORTIUS("저체력 시 마법피해 흡수", "Magic Damage Shield at Low Health"), // 맬모셔스의 아귀
    SPEAR_OF_SHOJIN("스킬 피해 증폭", "Ability Damage Amplification"), // 쇼진의 창
    HULLBREAKER("단독 행동 시 능력치 강화", "Stat Boost When Alone"), // 선체파괴자
    TITANIC_HYDRA("체력 비례 광역 피해", "Health-Based Area Damage"), // 거대한 히드라
    DEATH_DANCE("피해 지연 및 경감", "Delayed & Reduced Damage Taken"), // 죽음의 무도
    CHEMPUNK_CHAINSWORD("치유 감소 부여", "Applies Healing Reduction"), // 화공 펑크 사슬검
    SUNDERED_SKY("기본 공격 강화 및 체력 회복", "Empowered Attack & Self-Heal"), // 갈라진 하늘
    STRIDEBREAKER("둔화 및 광역 피해", "Slow & Area Damage"), // 발걸음 분쇄기
    ECLIPSE("연속 타격 시 추가피해·보호막", "Bonus Damage & Shield on Consecutive Hits"), // 월식

    // Marksman item traits
    FIENDHUNTER_BOLTS("궁극기 연계 치명타/피해 강화", "Ultimate-Triggered Crit/Damage Boost"), // 악마사냥꾼의 화살
    HEXOPTICS_C44("거리 비례 피해 증가, 처치 시 사거리 증가", "Distance-Based Damage Boost, Range Boost on Takedown"), // 마법광학 장치 C44
    INFINITY_EDGE("치명타 피해 극대화", "Maximized Critical Strike Damage"), // 무한의 대검
    YUN_TAL_WILDARROWS("공격 시 공격 속도 증가", "Attack Speed Boost on Attack"), // 윤 탈 야생화살
    MORTAL_REMINDER("대상 치유 감소", "Target Healing Reduction"), // 필멸자의 운명
    LORD_DOMINIK_REGARDS("탱커 방어관통", "Armor Penetration vs Tanks"), // 도미닉 경의 인사
    PHANTOM_DANCER("공속·이속 강화", "Attack Speed & Movement Speed Boost"), // 유령 무희
    BLOODTHIRSTER("생명흡수 및 보호막", "Life Steal & Shield"), // 피바라기
    RUNAAN_HURRICANE("평타 다중 타격", "Multi-Target Attacks"), // 루난의 허리케인
    STATIKK_SHIV("연쇄 마법 피해", "Chain Magic Damage"), // 스태틱의 단검
    RAPID_FIRECANNON("이동 후 강화 평타", "Empowered Attack After Moving"), // 고속 연사포
    STORMRAZOR("충전 후 강화 일격", "Empowered Strike When Charged"), // 폭풍갈퀴
    GUINSOO_RAGEBLADE("공속 상한 초과", "Exceeds Attack Speed Cap"), // 구인수의 격노검
    MERCURIAL_SCIMITAR("CC 정화 및 이속 증가", "CC Cleanse & Movement Speed Boost"), // 헤르메스의 시미터
    // 경계
    TERMINUS(
            "평타 시 방어력/마법저항 증가, 평타 시 방어구/마법 관통력 증가",
            "Armor/Magic Resist Boost on Attack, Armor/Magic Penetration Boost on Attack"),
    ESSENCE_REAVER("평타 시 마나 회복", "Mana Restore on Attack"), // 정수 약탈자
    KRAKEN_SLAYER("주기적 고정 피해", "Periodic True Damage"), // 크라켄 학살자
    IMMORTAL_SHIELDBOW("저체력 시 보호막", "Shield at Low Health"), // 불멸의 철갑궁
    NAVORI_FLICKERBLADE("평타 시 기본 스킬 재사용 대기시간 감소", "Basic Ability Cooldown Reduction on Attack"), // 나보리 명멸검
    THE_COLLECTOR("저체력 처형", "Low-Health Execute"), // 징수의 총

    // Assassin item traits
    // 요새파괴자
    BASTIONBREAKER(
            "포탑·오브젝트 고정 피해, 챔피언·몬스터 고정 피해",
            "True Damage to Turrets & Objectives, True Damage to Champions & Monsters"),
    YOUMUU_GHOSTBLADE("접근용 이동속도 강화", "Movement Speed Boost for Engaging"), // 요우무의 유령검
    UMBRAL_GLAIVE("은신 와드 탐지·제거", "Stealth Ward Detection & Removal"), // 그림자 검
    EDGE_OF_NIGHT("스킬 1회 무효화", "Blocks One Ability"), // 밤의 끝자락
    SERYLDA_GRUDGE("방어관통 및 둔화", "Armor Penetration & Slow"), // 세릴다의 원한
    SERPENT_FANG("적 보호막 제거", "Enemy Shield Removal"), // 독사의 송곳니
    AXIOM_ARC("처치 시 궁극기 쿨타임 감소", "Ultimate Cooldown Reduction on Takedown"), // 원칙의 원형낫
    HUBRIS("영구 공격력 증가", "Permanent Attack Damage Growth"), // 오만
    PROFANE_HYDRA("광역 평타 피해", "Area Attack Damage"), // 불경한 히드라
    VOLTAIC_CYCLOSWORD("충전 후 평타/스킬 강화", "Empowered Attack/Ability When Charged"), // 벼락폭풍검

    // Mage item traits
    BLACKFIRE_TORCH("지속 피해 강화", "Damage Over Time Boost"), // 어둠불꽃 횃불
    DUSK_AND_DAWN("스킬 연계 평타 강화", "Ability-Empowered Attacks"), // 황혼과 새벽
    ACTUALIZER("마나 기반 스킬 강화", "Mana-Based Ability Boost"), // 실체화 장비
    ARCHANGEL_STAFF("마나 기반 주문력 강화", "Mana-Based Ability Power Boost"), // 대천사의 지팡이
    MEJAI_SOULSTEALER("스택 기반 주문력 강화", "Stack-Based Ability Power Boost"), // 메자이의 영혼약탈자
    RABADON_DEATHCAP("주문력 극대화", "Maximized Ability Power"), // 라바돈의 죽음모자
    LICH_BANE("스킬 연계 평타 강화", "Ability-Empowered Attacks"), // 리치베인
    BANSHEE_VEIL("스킬 방어", "Ability Block"), // 밴시의 장막
    NASHOR_TOOTH("공격 속도/평타 강화", "Attack Speed/Basic Attack Boost"), // 내셔의 이빨
    RYLAI_CRYSTAL_SCEPTER("스킬에 둔화효과 추가", "Abilities Apply Slow"), // 라일라이의 수정홀
    MALIGNANCE("궁극기 강화", "Ultimate Boost"), // 악의
    VOID_STAFF("마법 관통 강화", "Magic Penetration Boost"), // 공허의 지팡이
    CRYPTBLOOM("마법 관통 강화/처치 연계 아군 회복", "Magic Penetration Boost/Ally Healing on Takedown"), // 무덤꽃
    HEXTECH_GUNBLADE("혼합 폭발적 피해", "Hybrid Burst Damage"), // 마법공학 총검
    HEXTECH_ROCKETBELT("짧은 거리 돌진", "Short Dash"), // 마법공학 로켓 벨트
    ZHONYA_HOURGLASS("일시적 무적", "Temporary Invulnerability"), // 존야의 모래시계
    MORELLONOMICON("치유 감소", "Healing Reduction"), // 모렐로노미콘
    HORIZON_FOCUS("장거리 스킬 강화", "Long-Range Ability Boost"), // 지평선의 초점
    COSMIC_DRIVE("스킬 연계 이동 속도 강화", "Ability-Triggered Movement Speed Boost"), // 우주의 추진력
    RIFTMAKER("전투 지속력 강화", "Sustained Combat Boost"), // 균열 생성기
    SHADOWFLAME("낮은 체력 적 피해 강화", "Increased Damage to Low-Health Enemies"), // 그림자불꽃
    STORMSURGE("폭발 피해", "Burst Damage"), // 폭풍 쇄도
    LIANDRY_TORMENT("체력 비례 지속 피해", "Health-Based Damage Over Time"), // 리안드리의 고통
    LUDEN_ECHO("스킬 폭발 피해", "Ability Burst Damage"), // 루덴의 메아리
    ROD_OF_AGES("시간 기반 체력·마나·주문력 성장", "Health, Mana & Ability Power Growth Over Time"), // 영겁의 지팡이
    BLOODLETTER_CURSE("지속적인 적 마법 저항력 감소", "Continuous Enemy Magic Resist Reduction"), // 핏빛 저주

    // Boots item traits
    BERSERKER_GREAVES("공격속도", "Attack Speed"), // 광전사의 군화
    BOOTS_OF_SWIFTNESS("둔화 효과 감소", "Slow Resistance"), // 신속의 장화
    SORCERER_SHOES("마법 관통력", "Magic Penetration"), // 마법사의 신발
    PLATED_STEELCAPS("평타 피해 감소", "Basic Attack Damage Reduction"), // 판금 장화
    MERCURY_TREADS("강인함", "Tenacity"), // 헤르메스의 발걸음
    IONIAN_BOOTS_OF_LUCIDITY("소환사 주문/스킬 가속", "Summoner Spell/Ability Haste"), // 명석함의 아이오니아 장화
    ;

    private final Map<String, String> displayName;

    ItemTrait(String korean, String english) {
        this.displayName = Map.of("ko-KR", korean, "en-US", english);
    }

    public Map<String, String> getDisplayName() {
        return displayName;
    }
}
