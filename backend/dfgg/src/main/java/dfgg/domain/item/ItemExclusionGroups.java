package dfgg.domain.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 동시에 보유할 수 없는 아이템들을 그룹으로 묶는다. Hard validity filter가 "이미 이 그룹의
 * 아이템을 들고 있다"를 판단하는 근거다.
 *
 * <p>두 종류의 규칙을 한 인터페이스로 합친다:
 * <ul>
 *     <li><b>신발</b> — Data Dragon 태그에서 유도한다. 새 신발이 추가돼도 목록을 고칠 필요가 없다.</li>
 *     <li><b>그 외</b> — 손으로 관리한다. Data Dragon은 "이 둘은 같이 못 산다"를 필드로
 *     표현하지 않고 설명 텍스트에만 담아서, 태그나 스탯으로는 유도할 수 없다.</li>
 * </ul>
 *
 * <p><b>이 목록은 완전하지 않다.</b> 게임 도메인 지식으로 채워야 하고 패치마다 바뀐다.
 * 빠진 규칙이 있으면 살 수 없는 아이템이 추천에 올라온다 — 반대로 잘못 넣으면 정답이 사라지므로,
 * 확실한 것만 넣는다.
 */
public final class ItemExclusionGroups {

    private static final String BOOTS_TAG = "Boots";
    private static final String BOOTS_GROUP = "BOOTS";

    private static final Map<Long, String> DEFAULT_GROUPS = Map.ofEntries(
            // 방어구 관통 고유 효과 — 하나만 적용된다
            Map.entry(3071L, "ARMOR_PENETRATION_UNIQUE"), // 칠흑의 양날도끼
            Map.entry(6694L, "ARMOR_PENETRATION_UNIQUE")  // 셰릴다의 원한
    );

    private final Map<Long, String> groupByItemId;

    public ItemExclusionGroups() {
        this(DEFAULT_GROUPS);
    }

    public ItemExclusionGroups(Map<Long, String> groupByItemId) {
        Objects.requireNonNull(groupByItemId, "배타 그룹 목록은 null일 수 없습니다.");
        this.groupByItemId = Map.copyOf(new HashMap<>(groupByItemId));
    }

    /**
     * 두 아이템을 동시에 보유할 수 없으면 {@code true}.
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
        Optional<String> oneGroup = groupOf(one);
        Optional<String> otherGroup = groupOf(other);
        return oneGroup.isPresent() && oneGroup.equals(otherGroup);
    }

    public Optional<String> groupOf(Item item) {
        Objects.requireNonNull(item, "아이템은 null일 수 없습니다.");
        if (item.hasTag(BOOTS_TAG)) {
            return Optional.of(BOOTS_GROUP);
        }
        return Optional.ofNullable(groupByItemId.get(item.getItemId()));
    }
}
