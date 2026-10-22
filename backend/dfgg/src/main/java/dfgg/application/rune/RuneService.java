package dfgg.application.rune;

import dfgg.domain.rune.Rune;
import dfgg.domain.rune.RuneKind;
import dfgg.domain.rune.RuneRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.RuneResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Data Dragon 최신 빌드의 룬 계열·룬과 고정 파편 목록을 룬 카탈로그로 저장한다.
 * 이름은 DB에, 아이콘은 S3의 최신 패치 폴더에 둔다. 추천 응답은 이 둘만 쓰고 Data Dragon을 부르지 않는다.
 */
@Service
public class RuneService {

    private final DataDragonClient dataDragonClient;
    private final RuneRepository runeRepository;
    private final RuneImageService runeImageService;

    public RuneService(DataDragonClient dataDragonClient, RuneRepository runeRepository,
                       RuneImageService runeImageService) {
        this.dataDragonClient = dataDragonClient;
        this.runeRepository = runeRepository;
        this.runeImageService = runeImageService;
    }

    public void syncRunes() {
        RuneResponse response = dataDragonClient.getRunes();
        List<Rune> runes = new ArrayList<>();
        for (RuneResponse.Style style : response.styles()) {
            runes.add(store(response, style.id(), RuneKind.STYLE, style.name(), style.icon()));
            style.perks().forEach(perk ->
                    runes.add(store(response, perk.id(), RuneKind.PERK, perk.name(), perk.icon())));
        }
        for (StatShardCatalog shard : StatShardCatalog.values()) {
            runeImageService.store(response.version(), shard.runeId(), shard.iconPath());
            runes.add(shard.toRune());
        }
        runeRepository.saveAll(runes);
    }

    private Rune store(RuneResponse response, int runeId, RuneKind kind, String koreanName, String iconPath) {
        Rune rune = new Rune(runeId, kind, response.localizedName(runeId, koreanName));
        runeImageService.store(response.version(), runeId, iconPath);
        return rune;
    }
}
