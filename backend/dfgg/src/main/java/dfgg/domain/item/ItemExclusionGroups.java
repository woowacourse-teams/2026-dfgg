package dfgg.domain.item;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 동시에 보유할 수 없는 아이템들을 그룹으로 묶는다. Hard validity filter가 "이미 이 그룹의
 * 아이템을 들고 있다"를 판단하는 근거다.
 * <p>
 * 한 아이템이 여러 그룹에 동시에 속할 수 있다. 예를 들어 피의 노래는 주문검
 * 계열(Spellblade — 삼위일체·리치베인 등과 공유하는 고유 효과)이면서 동시에 세계의 결실
 * 계열(자자크·태양의 썰매 등과 공유하는 팀 지원 고유 효과)이다. 어느 한쪽만 넣으면 다른 쪽
 * 배타 관계를 놓친다.
 * <p>
 * 두 종류의 규칙을 한 인터페이스로 합친다:
 * <ul>
 *     <li><b>신발</b> — Data Dragon 태그에서 유도한다. 새 신발이 추가돼도 목록을 고칠 필요가 없다.</li>
 *     <li><b>그 외</b> — 손으로 관리한다. Data Dragon은 "이 둘은 같이 못 산다"를 필드로
 *     표현하지 않고 설명 텍스트에만 담아서, 태그나 스탯으로는 유도할 수 없다.</li>
 * </ul>
 *
 * <p>이 목록은 완전하지 않다. 게임 도메인 지식으로 채워야 하고 패치마다 바뀐다.
 * 빠진 규칙이 있으면 살 수 없는 아이템이 추천에 올라온다 — 반대로 잘못 넣으면 정답이 사라지므로, 확실한 것만 넣는다.
 */
public final class ItemExclusionGroups {

    private static final String BOOTS_TAG = "Boots";
    private static final String BOOTS_GROUP = "BOOTS";

    private static final Map<Long, Set<String>> DEFAULT_GROUPS = buildDefaultGroups();

    private final Map<Long, Set<String>> groupsByItemId;

    public ItemExclusionGroups() {
        this(DEFAULT_GROUPS);
    }

    public ItemExclusionGroups(Map<Long, Set<String>> groupsByItemId) {
        Objects.requireNonNull(groupsByItemId, "배타 그룹 목록은 null일 수 없습니다.");
        Map<Long, Set<String>> copied = new HashMap<>();
        groupsByItemId.forEach((itemId, groups) -> copied.put(itemId, Set.copyOf(groups)));
        this.groupsByItemId = Map.copyOf(copied);
    }

    /**
     * 두 아이템을 동시에 보유할 수 없으면 {@code true} — 공유하는 그룹이 하나라도 있으면 배타다.
     *
     * <p>같은 아이템끼리는 배타로 보지 않는다 — 중복 보유는 "이미 산 아이템" 규칙이 따로 막는다.
     * 여기서까지 막으면 두 규칙이 같은 일을 하게 되고, 어느 쪽이 걸렀는지 알 수 없어진다.
     */
    public boolean isExclusiveWith(Item one, Item other) {
        Objects.requireNonNull(one, "아이템은 null일 수 없습니다.");
        Objects.requireNonNull(other, "아이템은 null일 수 없습니다.");
        if (Objects.equals(one.getItemId(), other.getItemId())) {
            return false;
        }
        return !Collections.disjoint(groupsOf(one), groupsOf(other));
    }

    public Set<String> groupsOf(Item item) {
        Objects.requireNonNull(item, "아이템은 null일 수 없습니다.");
        if (item.hasTag(BOOTS_TAG)) {
            return Set.of(BOOTS_GROUP);
        }
        return groupsByItemId.getOrDefault(item.getItemId(), Set.of());
    }

    private static Map<Long, Set<String>> buildDefaultGroups() {
        Map<Long, Set<String>> groups = new HashMap<>();

        // 주문검(Spellblade) 계열 — 기본 공격/스킬 강화 고유 효과가 하나만 적용된다
        addToGroup(groups, "SPELLBLADE",
                3078L, // 삼위일체
                3100L, // 리치베인
                6662L, // 얼어붙은 건틀릿
                3508L, // 정수 약탈자
                2510L, // 황혼과 새벽
                3877L  // 피의 노래
        );

        // 생명선(Lifeline) 계열 — 체력 낮을 때 발동하는 실드 고유 효과가 하나만 적용된다
        addToGroup(groups, "LIFELINE",
                6673L, // 불멸의 철갑궁
                3156L, // 맬모셔스의 아귀
                3053L,  // 스테락의 도전
                2525L, // 원형질 안전벨트
                3040L // 대천사의 포옹
        );

        // 티아멧(Tiamat) 계열 — 광역 공격 고유 효과가 하나만 적용된다
        addToGroup(groups, "TIAMAT",
                3074L, // 굶주린 히드라
                3748L, // 거대한 히드라
                6698L, // 불경한 히드라
                6631L  // 발걸음 분쇄기
        );

        // 방어구 관통력 고유 효과 — 하나만 적용된다
        addToGroup(groups, "ARMOR_PENETRATION_UNIQUE",
                3071L, // 칠흑의 양날 도끼
                3036L, // 도미닉 경의 인사
                3033L, // 필멸자의 운명
                3302L, // 경계
                6694L  // 세릴다의 원한
        );

        // 마법 관통력 고유 효과 — 하나만 적용된다. 경계는 방어구·마법 관통 그룹에 동시에 속한다.
        addToGroup(groups, "MAGIC_PENETRATION_UNIQUE",
                3302L, // 경계
                3137L, // 무덤꽃
                3135L, // 공허의 지팡이
                8010L  // 핏빛 저주
        );

        // 무효화(Annul) 계열 — 스킬 무효화 실드 고유 효과가 하나만 적용된다
        addToGroup(groups, "ANNUL",
                3102L, // 밴시의 장막
                3814L  // 밤의 끝자락
        );

        // 불사르기(Immolate) 계열 — 지속 화상 피해 고유 효과가 하나만 적용된다
        addToGroup(groups, "IMMOLATE",
                6664L, // 공허한 광휘
                3068L  // 태양불꽃 방패
        );

        // 세계의 결실 계열 — 팀 지원 고유 효과가 하나만 적용된다. 피의 노래는 주문검 그룹과 겹친다.
        addToGroup(groups, "WORLD_ENDER",
                3871L, // 자자크의 세계가시
                3877L, // 피의 노래
                3876L, // 태양의 썰매
                3870L, // 꿈 생성기
                3869L  // 천상의 이의
        );

        return groups;
    }

    private static void addToGroup(Map<Long, Set<String>> groups, String groupName, Long... itemIds) {
        for (Long itemId : itemIds) {
            groups.computeIfAbsent(itemId, id -> new java.util.HashSet<>()).add(groupName);
        }
    }
}
