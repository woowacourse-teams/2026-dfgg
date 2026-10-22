package dfgg.application.spell;

import dfgg.domain.spell.SummonerSpell;
import dfgg.domain.spell.SummonerSpellRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.SummonerSpellResponse;
import dfgg.infrastructure.external.dto.SummonerSpellResponse.SummonerSpellData;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Data Dragon 소환사 주문 중 협곡 주문만 적재한다. 다른 모드 주문은 영문 이름이 겹쳐(아레나 점멸) 이름 조회를 흐린다.
 * id가 같으면 덮어쓰므로 다시 돌려도 행이 늘지 않는다.
 */
@Service
public class SummonerSpellService {

    private final DataDragonClient dataDragonClient;
    private final SummonerSpellRepository spellRepository;
    private final SummonerSpellImageService spellImageService;

    public SummonerSpellService(DataDragonClient dataDragonClient, SummonerSpellRepository spellRepository,
                                SummonerSpellImageService spellImageService) {
        this.dataDragonClient = dataDragonClient;
        this.spellRepository = spellRepository;
        this.spellImageService = spellImageService;
    }

    public void syncSpells() {
        SummonerSpellResponse response = dataDragonClient.getSummonerSpells();
        List<SummonerSpell> spells = response.data().values().stream()
                .filter(SummonerSpellData::isSummonersRift)
                .map(spell -> toSpell(spell, response))
                .toList();
        spells.forEach(spell -> spellImageService.store(response.version(), response.dataVersion(),
                spell.getRiotKey(), spell.getIconKey()));
        spellRepository.saveAll(spells);
    }

    private SummonerSpell toSpell(SummonerSpellData spell, SummonerSpellResponse response) {
        if (spell.image() == null) {
            throw new IllegalStateException("[Error] 소환사 주문 이미지 정보가 없습니다: " + spell.id());
        }
        return new SummonerSpell(Integer.parseInt(spell.key()), spell.id(), response.localizedName(spell.id()),
                spell.image().full());
    }
}
