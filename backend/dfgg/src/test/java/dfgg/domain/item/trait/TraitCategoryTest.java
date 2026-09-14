package dfgg.domain.item.trait;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * trait 어휘를 리그 오브 레전드 역할군으로 나눈다 — 암살자, 전사, 마법사, 원거리 딜러, 서포터, 탱커
 * <p>
 * 나누는 기준이 아이템의 역할이라 한 trait가 여러 파일에 나타난다 —
 * {@code CRITICAL_STRIKE}는 전사·원거리 딜러·암살자 모두에게 자연스럽다.
 * 그래서 "한 trait는 한 파일에만"이라는 규칙은 두지 않는다.
 * 대신 파일이 늘거나 줄 때 조용히 어긋나는 것들을 여기서 잡는다.
 * <p>
 * 여러 파일에 걸친 아이템을 카탈로그가 합치는지는 {@link ItemTraitCatalogMergeTest}가 본다.
 */
class TraitCategoryTest {

    private record RoleFile(String name, Map<Long, Set<ItemTrait>> byItemId) {
    }

    /** 카탈로그의 {@code merge(...)} 인자와 같아야 한다. 한쪽만 늘리면 이 클래스의 테스트가 잡는다. */
    private static List<RoleFile> roleFiles() {
        return List.of(
                new RoleFile("전사", FighterTraits.BY_ITEM_ID),
                new RoleFile("원거리 딜러", MarksmanTraits.BY_ITEM_ID),
                new RoleFile("암살자", AssassinTraits.BY_ITEM_ID),
                new RoleFile("마법사", MageTraits.BY_ITEM_ID),
                new RoleFile("탱커", TankTraits.BY_ITEM_ID),
                new RoleFile("서포터", ItemProfile.traitsByItemId(SupportTraits.BY_ITEM_ID)));
    }

    @Test
    @DisplayName("모든 역할 파일이 카탈로그에 연결돼 있다 — 빠뜨리면 그 파일 전체가 조용히 사라진다")
    void everyRoleFile_IsWiredIntoTheCatalog() {
        // merge(...) 인자에서 파일 하나를 빠뜨려도 컴파일은 되고 테스트도 대부분 통과한다.
        // 그 아이템들만 특성이 없어질 뿐이라 응답을 하나하나 보지 않으면 모른다.
        ItemTraitCatalog catalog = new ItemTraitCatalog();
        List<String> unreachable = new ArrayList<>();

        for (RoleFile roleFile : roleFiles()) {
            roleFile.byItemId().forEach((itemId, traits) -> {
                if (!catalog.traitsOf(new Item(itemId, "아이템 " + itemId)).containsAll(traits)) {
                    unreachable.add("%s의 %d".formatted(roleFile.name(), itemId));
                }
            });
        }

        assertThat(unreachable).isEmpty();
    }

    @Test
    @DisplayName("역할 파일마다 아이템이 들어 있다 — 빈 파일은 실수다")
    void everyRoleFile_HasEntries() {
        assertThat(roleFiles()).allSatisfy(roleFile ->
                assertThat(roleFile.byItemId()).as(roleFile.name()).isNotEmpty());
    }

    @Test
    @DisplayName("아레나 변형 아이템 ID를 쓰지 않는다 — 소환사의 협곡 경기에는 나오지 않는다")
    void mappings_UseBaseItemIdsNotArenaVariants() {
        // 같은 아이템이 두 ID로 존재한다. 월석 재생기는 6617과 326617이다.
        // 32xxxx는 아레나 전용이라 추천 대상 경기(큐 420)에는 등장하지 않는다.
        List<String> arenaVariants = new ArrayList<>();

        for (RoleFile roleFile : roleFiles()) {
            roleFile.byItemId().keySet().stream()
                    .filter(itemId -> itemId >= 300_000L)
                    .forEach(itemId -> arenaVariants.add(
                            "%s에 %d".formatted(roleFile.name(), itemId)));
        }

        assertThat(arenaVariants).isEmpty();
    }

    @Test
    @DisplayName("아이템 목록이 통째로 유실되지 않았다 — 특성은 손으로 채우는 중이다")
    void mappings_StillCoverMostOfTheItemPool() {
        long entries = roleFiles().stream()
                .mapToLong(roleFile -> roleFile.byItemId().size())
                .sum();
        long withTraits = roleFiles().stream()
                .flatMap(roleFile -> roleFile.byItemId().values().stream())
                .filter(traits -> !traits.isEmpty())
                .count();

        System.out.printf("특성 매김 진행: %d / %d 항목%n", withTraits, entries);

        assertThat(entries)
                .as("역할 파일 전체의 아이템 수. 대량 유실을 잡는 하한이다")
                .isGreaterThanOrEqualTo(100);
    }
}
