package dfgg.application.propick;

import dfgg.domain.catalog.EnglishNameIndex;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.propick.ProLeagues;
import dfgg.domain.propick.ProPlayer;
import dfgg.domain.propick.ProTournament;
import dfgg.domain.rune.RuneRepository;
import dfgg.domain.spell.SummonerSpellRepository;
import dfgg.infrastructure.config.ProPickProperties;
import dfgg.infrastructure.external.client.LeaguepediaClient;
import dfgg.infrastructure.external.client.LeaguepediaException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 설정한 주요 리그마다 최근 N일 대회 픽을 Leaguepedia에서 받아 저장한다 ({@code POST /admin/mining/pro-picks}).
 * 리그 하나가 실패해도 나머지는 계속하고, 실패한 리그는 결과에 사유를 남긴다.
 */
@Service
public class ProPickCollectionService {

    private final LeaguepediaClient leaguepediaClient;
    private final ProScoreboardSource scoreboardSource;
    private final ProPickRowConverter converter;
    private final ProMetadataService metadataService;
    private final ProPickWriter pickWriter;
    private final ProLeagues leagues;
    private final ProPickProperties properties;
    private final ChampionRepository championRepository;
    private final SummonerSpellRepository spellRepository;
    private final ItemRepository itemRepository;
    private final RuneRepository runeRepository;
    private final Clock clock;

    public ProPickCollectionService(LeaguepediaClient leaguepediaClient, ProScoreboardSource scoreboardSource,
                                    ProPickRowConverter converter, ProMetadataService metadataService,
                                    ProPickWriter pickWriter, ProLeagues leagues, ProPickProperties properties,
                                    ChampionRepository championRepository, SummonerSpellRepository spellRepository,
                                    ItemRepository itemRepository, RuneRepository runeRepository, Clock clock) {
        this.leaguepediaClient = leaguepediaClient;
        this.scoreboardSource = scoreboardSource;
        this.converter = converter;
        this.metadataService = metadataService;
        this.pickWriter = pickWriter;
        this.leagues = leagues;
        this.properties = properties;
        this.championRepository = championRepository;
        this.spellRepository = spellRepository;
        this.itemRepository = itemRepository;
        this.runeRepository = runeRepository;
        this.clock = clock;
    }

    public ProPickCollectionResult collect() {
        try {
            leaguepediaClient.login();
        } catch (LeaguepediaException exception) {
            return new ProPickCollectionResult(leagues.prefixes().stream()
                    .map(league -> ProLeagueCollectionResult.failed(league, exception.getMessage()))
                    .toList());
        }
        LocalDate since = LocalDate.now(clock).minusDays(properties.lookbackDays());
        ProPickCatalog catalog = catalog();
        return new ProPickCollectionResult(leagues.prefixes().stream()
                .map(league -> collectLeague(league, since, catalog))
                .toList());
    }

    private ProLeagueCollectionResult collectLeague(String league, LocalDate since, ProPickCatalog catalog) {
        try {
            List<Map<String, String>> rows = scoreboardSource.fetchLeague(league, since);
            List<ProPickConversion> conversions = rows.stream().map(row -> converter.convert(row, catalog)).toList();
            List<ProPickDraft> drafts = conversions.stream().flatMap(conversion -> conversion.draft().stream()).toList();
            return ProLeagueCollectionResult.collected(league, rows.size(), save(drafts), skippedOf(conversions));
        } catch (LeaguepediaException exception) {
            return ProLeagueCollectionResult.failed(league, exception.getMessage());
        }
    }

    private int save(List<ProPickDraft> drafts) {
        if (drafts.isEmpty()) {
            return 0;
        }
        Map<String, ProTournament> tournaments = metadataService.upsertTournaments(
                drafts.stream().map(ProPickDraft::overviewPage).distinct().toList());
        Map<String, ProPlayer> players = metadataService.upsertPlayers(
                drafts.stream().map(ProPickDraft::playerPage).distinct().toList());
        return pickWriter.save(drafts, tournaments, players);
    }

    private Map<ProPickSkipReason, Integer> skippedOf(List<ProPickConversion> conversions) {
        Map<ProPickSkipReason, Integer> skipped = new EnumMap<>(ProPickSkipReason.class);
        conversions.stream()
                .filter(conversion -> conversion.draft().isEmpty())
                .forEach(conversion -> skipped.merge(conversion.skipReason(), 1, Integer::sum));
        return skipped;
    }

    // 수집 한 번에 한 번만 만든다. 리그마다 행 수천 개를 이 색인으로 바꾼다.
    private ProPickCatalog catalog() {
        return new ProPickCatalog(
                EnglishNameIndex.ofChampions(championRepository.findAll()),
                EnglishNameIndex.ofSpells(spellRepository.findAll()),
                EnglishNameIndex.ofItems(itemRepository.findAll()),
                EnglishNameIndex.ofRunes(runeRepository.findAll()));
    }
}
