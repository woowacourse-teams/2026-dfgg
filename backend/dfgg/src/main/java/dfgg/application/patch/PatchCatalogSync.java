package dfgg.application.patch;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.rune.RuneService;
import dfgg.application.spell.SummonerSpellService;
import dfgg.domain.match.PatchVersion;
import dfgg.infrastructure.external.client.DataDragonClient;

/**
 * 새 패치의 카탈로그를 main에 저장하고 그림을 S3 패치 폴더에 올린다. main을 쓰는 서버(scheduling)에서만 동작한다.
 * 응답 URL이 버전 폴더를 쓰는 챔피언·아이템·소환사 주문과, 룬 추천 패치 폴더를 쓰는 룬을 함께 올린다.
 */
public class PatchCatalogSync {

    private final ChampionService champions;
    private final ItemService items;
    private final SummonerSpellService spells;
    private final RuneService runes;
    private final DataDragonClient dataDragon;
    private final PatchImageChecker images;

    public PatchCatalogSync(ChampionService champions, ItemService items, SummonerSpellService spells,
                            RuneService runes, DataDragonClient dataDragon, PatchImageChecker images) {
        this.champions = champions;
        this.items = items;
        this.spells = spells;
        this.runes = runes;
        this.dataDragon = dataDragon;
        this.images = images;
    }

    /** 모두 성공하고 그사이 Data Dragon 패치가 그대로일 때만 완료 표시를 남긴다. */
    public void syncFor(PatchVersion patch) {
        champions.syncChampions();
        items.syncItems();
        spells.syncSpells();
        runes.syncRunes();
        PatchVersion synced = PatchVersion.of(dataDragon.getLatestVersion());
        if (!synced.equals(patch)) {
            throw new IllegalStateException("동기화하는 사이 Data Dragon 패치가 바뀌었습니다: " + patch.value() + " -> " + synced.value());
        }
        images.markReady(patch);
    }
}
