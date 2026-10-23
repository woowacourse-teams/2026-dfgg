package dfgg.application.propick;

import dfgg.domain.champion.Champion;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.item.Item;
import dfgg.domain.language.Language;
import dfgg.domain.propick.ProPick;
import dfgg.domain.propick.ProPickRune;
import dfgg.domain.propick.ProPlayer;
import dfgg.domain.rune.Rune;
import dfgg.domain.spell.SummonerSpell;
import dfgg.presentation.dto.response.TodayPickResponse.ChampionResponse;
import dfgg.presentation.dto.response.TodayPickResponse.EntryResponse;
import dfgg.presentation.dto.response.TodayPickResponse.PickResponse;
import dfgg.presentation.dto.response.TodayPickResponse.PlayerResponse;
import dfgg.presentation.dto.response.TodayPickResponse.RunesResponse;
import dfgg.presentation.dto.response.TodayPickResponse.TreeResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 뽑힌 픽에 이름·사진을 붙인다. 카탈로그는 뽑힌 픽의 id만 미리 한 번에 읽어 온다.
 * 룬 칸은 0 핵심, 1~3 주 트리, 4~5 보조 트리, 6~8 파편이고, 트리는 0번·4번 칸 룬의 트리다.
 */
class TodayPickCatalog {

    private static final int KEYSTONE_SLOT = 0;
    private static final int FIRST_SECONDARY_SLOT = 4;
    private static final int FIRST_SHARD_SLOT = 6;

    private final Map<Long, Champion> champions;
    private final Map<Integer, SummonerSpell> spells;
    private final Map<Long, Item> items;
    private final Map<Integer, Rune> runes;
    private final ImageUrls imageUrls;
    private final Language language;

    TodayPickCatalog(Map<Long, Champion> champions, Map<Integer, SummonerSpell> spells, Map<Long, Item> items,
                     Map<Integer, Rune> runes, ImageUrls imageUrls, Language language) {
        this.champions = champions;
        this.spells = spells;
        this.items = items;
        this.runes = runes;
        this.imageUrls = imageUrls;
        this.language = language;
    }

    PickResponse toResponse(ProPick pick) {
        ProPlayer player = pick.getPlayer();
        return new PickResponse(
                new PlayerResponse(player.getName(), player.getTeam(), player.getImageUrl()),
                pick.getPlayedAt().toLocalDate(),
                championOf(pick),
                pick.getSpells().stream().map(spell -> spellOf(spell.getSpellId())).flatMap(Optional::stream).toList(),
                pick.getItems().stream().map(item -> itemOf(item.getItemId())).flatMap(Optional::stream).toList(),
                runesOf(pick.getRunes()));
    }

    private ChampionResponse championOf(ProPick pick) {
        Champion champion = champions.get(pick.getChampionId());
        return new ChampionResponse(pick.getChampionId(), language.pick(champion.getName()),
                imageUrls.championOf(champion.getRiotKey()), pick.getPosition());
    }

    // 카탈로그에서 빠진 항목은 응답에서 뺀다.
    private Optional<EntryResponse> spellOf(Integer spellId) {
        return Optional.ofNullable(spells.get(spellId))
                .map(spell -> new EntryResponse(spellId.longValue(), language.pick(spell.getName()),
                        imageUrls.summonerSpellOf(spell.getRiotKey())));
    }

    private Optional<EntryResponse> itemOf(Long itemId) {
        return Optional.ofNullable(items.get(itemId))
                .map(item -> new EntryResponse(itemId, language.pick(item.getName()), imageUrls.itemOf(itemId)));
    }

    private RunesResponse runesOf(List<ProPickRune> pickRunes) {
        return new RunesResponse(
                treeOf(pickRunes, KEYSTONE_SLOT),
                runesIn(pickRunes, KEYSTONE_SLOT, KEYSTONE_SLOT).stream().findFirst().orElse(null),
                runesIn(pickRunes, KEYSTONE_SLOT + 1, FIRST_SECONDARY_SLOT - 1),
                treeOf(pickRunes, FIRST_SECONDARY_SLOT),
                runesIn(pickRunes, FIRST_SECONDARY_SLOT, FIRST_SHARD_SLOT - 1),
                runesIn(pickRunes, FIRST_SHARD_SLOT, Integer.MAX_VALUE));
    }

    private TreeResponse treeOf(List<ProPickRune> pickRunes, int slot) {
        return pickRunes.stream()
                .filter(pickRune -> pickRune.getSlot() == slot)
                .map(pickRune -> runes.get(pickRune.getRuneId()))
                .filter(Objects::nonNull)
                .map(rune -> runes.get(rune.treeId()))
                .filter(Objects::nonNull)
                .map(tree -> new TreeResponse((long) tree.runeId(), tree.nameIn(language)))
                .findFirst()
                .orElse(null);
    }

    private List<EntryResponse> runesIn(List<ProPickRune> pickRunes, int fromSlot, int toSlot) {
        return pickRunes.stream()
                .filter(pickRune -> pickRune.getSlot() >= fromSlot && pickRune.getSlot() <= toSlot)
                .map(pickRune -> runes.get(pickRune.getRuneId()))
                .filter(Objects::nonNull)
                .map(rune -> new EntryResponse((long) rune.runeId(), rune.nameIn(language),
                        imageUrls.runeOf(imageUrls.version(), rune.runeId())))
                .toList();
    }
}
