package dfgg.domain.item.trait;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 아이템이 아군과 시너지를 내는지는 특성을 붙이는 항목에서 함께 선언한다.
 * <p>
 * 응답의 ally는 ALLY 아이템에만 채운다. 선언하지 않은 아이템은 SELF다 —
 * 필멸자의 운명에 아군 이름이 붙는 것보다 비어 있는 게 낫다.
 */
class ItemSynergyTest {

    private final ItemTraitCatalog catalog = new ItemTraitCatalog();

    @Test
    @DisplayName("아군에게 작용하는 서포터 아이템은 ALLY다")
    void synergyOf_WhenSupportItemActsOnAllies_IsAlly() {
        // given
        Item mikaelsBlessing = new Item(3222L, "미카엘의 축복");
        Item ardentCenser = new Item(3504L, "불타는 향로");

        // when
        Synergy mikaelsSynergy = catalog.synergyOf(mikaelsBlessing);
        Synergy censerSynergy = catalog.synergyOf(ardentCenser);

        // then
        assertThat(mikaelsSynergy).isEqualTo(Synergy.ALLY);
        assertThat(censerSynergy).isEqualTo(Synergy.ALLY);
    }

    @Test
    @DisplayName("서포터 파일에 있어도 자기에게만 작용하면 SELF다 — 포지션이 아니라 아이템이 기준이다")
    void synergyOf_WhenSupportItemActsOnlyOnSelf_IsSelf() {
        // given
        Item zazzaksRealmspike = new Item(3871L, "자자크의 세계가시");

        // when
        Synergy synergy = catalog.synergyOf(zazzaksRealmspike);

        // then
        assertThat(synergy).isEqualTo(Synergy.SELF);
    }

    @Test
    @DisplayName("synergy를 선언하지 않은 역할 파일의 아이템은 SELF다")
    void synergyOf_WhenItemIsFromUndeclaredRoleFile_IsSelf() {
        // given
        Item mortalReminder = new Item(3033L, "필멸자의 운명");

        // when
        Synergy synergy = catalog.synergyOf(mortalReminder);

        // then
        assertThat(synergy).isEqualTo(Synergy.SELF);
    }

    @Test
    @DisplayName("어느 파일에도 없는 아이템은 SELF다")
    void synergyOf_WhenItemIsUnmapped_IsSelf() {
        // given
        Item doransShield = new Item(1054L, "도란의 방패");

        // when
        Synergy synergy = catalog.synergyOf(doransShield);

        // then
        assertThat(synergy).isEqualTo(Synergy.SELF);
    }

    @Test
    @DisplayName("ally 팩토리는 ALLY synergy와 주어진 특성을 한 항목에 담는다")
    void ally_WhenTraitsGiven_CarriesAllySynergyAndThoseTraits() {
        // given
        ItemTrait cleanse = ItemTrait.CC_CLEANSE;

        // when
        ItemProfile profile = ItemProfile.ally(cleanse);

        // then
        assertThat(profile.synergy()).isEqualTo(Synergy.ALLY);
        assertThat(profile.traits()).containsExactly(cleanse);
    }

    @Test
    @DisplayName("self 팩토리는 특성 없이도 SELF 항목을 만든다")
    void self_WhenNoTraitsGiven_CarriesSelfSynergyAndEmptyTraits() {
        // when
        ItemProfile profile = ItemProfile.self();

        // then
        assertThat(profile.synergy()).isEqualTo(Synergy.SELF);
        assertThat(profile.traits()).isEmpty();
    }

    @Test
    @DisplayName("synergy 매핑을 직접 주입하면 그것만 쓴다")
    void synergyOf_WhenCatalogIsCustom_UsesOnlyTheGivenMapping() {
        // given
        ItemTraitCatalog custom = new ItemTraitCatalog(
                Map.of(3871L, Set.of(ItemTrait.SKILL_POKE)),
                Map.of(3871L, Synergy.ALLY));

        // when
        Synergy declared = custom.synergyOf(new Item(3871L, "자자크의 세계가시"));
        Synergy undeclared = custom.synergyOf(new Item(3222L, "미카엘의 축복"));

        // then
        assertThat(declared).isEqualTo(Synergy.ALLY);
        assertThat(undeclared).isEqualTo(Synergy.SELF);
    }
}
