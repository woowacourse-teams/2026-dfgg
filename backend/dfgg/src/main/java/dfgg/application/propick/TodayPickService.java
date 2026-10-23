package dfgg.application.propick;

import dfgg.common.exception.ChampionNotFoundException;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.language.Language;
import dfgg.domain.propick.ProPick;
import dfgg.domain.propick.ProPickDraw;
import dfgg.domain.propick.ProPickItem;
import dfgg.domain.propick.ProPickRepository;
import dfgg.domain.propick.ProPickRune;
import dfgg.domain.propick.ProPickSpell;
import dfgg.domain.propick.ProTournament;
import dfgg.domain.propick.ProTournamentRepository;
import dfgg.domain.rune.Rune;
import dfgg.domain.rune.RuneRepository;
import dfgg.domain.spell.SummonerSpell;
import dfgg.domain.spell.SummonerSpellRepository;
import dfgg.presentation.dto.response.TodayPickResponse;
import dfgg.presentation.dto.response.TodayPickResponse.TournamentResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 추천 픽. 최근 대회(마지막 경기가 가장 늦은 대회)의 내 라인 픽에서 밴을 빼고 챔피언이 겹치지 않게 무작위로 고른다.
 */
@Service
public class TodayPickService {

    // 양 팀 5개씩. 같은 챔피언을 양 팀이 밴할 수 있어 중복도 개수에 든다.
    private static final int MAX_BANS = 10;

    private final ProTournamentRepository tournamentRepository;
    private final ProPickRepository pickRepository;
    private final ChampionRepository championRepository;
    private final SummonerSpellRepository spellRepository;
    private final ItemRepository itemRepository;
    private final RuneRepository runeRepository;
    private final ImageUrls imageUrls;
    private final ProPickDraw draw;
    private final Supplier<RandomGenerator> randomSupplier;

    @Autowired
    public TodayPickService(ProTournamentRepository tournamentRepository, ProPickRepository pickRepository,
                            ChampionRepository championRepository, SummonerSpellRepository spellRepository,
                            ItemRepository itemRepository, RuneRepository runeRepository, ImageUrls imageUrls,
                            @Value("${counter.today-picks}") int todayPicks) {
        this(tournamentRepository, pickRepository, championRepository, spellRepository, itemRepository,
                runeRepository, imageUrls, todayPicks, ThreadLocalRandom::current);
    }

    TodayPickService(ProTournamentRepository tournamentRepository, ProPickRepository pickRepository,
                     ChampionRepository championRepository, SummonerSpellRepository spellRepository,
                     ItemRepository itemRepository, RuneRepository runeRepository, ImageUrls imageUrls,
                     int todayPicks, Supplier<RandomGenerator> randomSupplier) {
        this.tournamentRepository = tournamentRepository;
        this.pickRepository = pickRepository;
        this.championRepository = championRepository;
        this.spellRepository = spellRepository;
        this.itemRepository = itemRepository;
        this.runeRepository = runeRepository;
        this.imageUrls = imageUrls;
        this.draw = new ProPickDraw(todayPicks);
        this.randomSupplier = randomSupplier;
    }

    @Transactional(readOnly = true)
    public TodayPickResponse recommend(ChampionPosition position, List<Integer> bans, Language language) {
        validateBans(bans);
        Optional<ProTournament> latest = tournamentRepository.findLatestPlayed();
        if (latest.isEmpty()) {
            return TodayPickResponse.empty(position);
        }
        ProTournament tournament = latest.get();
        List<ProPick> drawn = draw.draw(pickRepository.findByTournamentAndPosition(tournament.getId(), position),
                bans, randomSupplier.get());
        TodayPickCatalog catalog = catalogOf(drawn, language);
        return new TodayPickResponse(
                new TournamentResponse(tournament.getName(), tournament.getLeague(), tournament.getStartDate()),
                position,
                drawn.stream().map(catalog::toResponse).toList());
    }

    private void validateBans(List<Integer> bans) {
        if (bans.size() > MAX_BANS) {
            throw new InvalidRecommendationRequestException("밴은 " + MAX_BANS + "개까지입니다: " + bans.size() + "개");
        }
        Set<Long> known = championRepository.findAllById(bans.stream().map(Integer::longValue).distinct().toList())
                .stream()
                .map(Champion::getChampionId)
                .collect(Collectors.toSet());
        List<Integer> unknown = bans.stream().filter(ban -> !known.contains(ban.longValue())).distinct().toList();
        if (!unknown.isEmpty()) {
            throw new ChampionNotFoundException(unknown.toString());
        }
    }

    // 뽑힌 픽의 id만 카탈로그마다 한 번씩 읽는다. 룬 트리는 룬을 읽은 뒤 한 번 더 읽는다.
    private TodayPickCatalog catalogOf(List<ProPick> picks, Language language) {
        Map<Long, Champion> champions = byId(championRepository.findAllById(
                picks.stream().map(ProPick::getChampionId).distinct().toList()), Champion::getChampionId);
        Map<Integer, SummonerSpell> spells = byId(spellRepository.findAllById(picks.stream()
                        .flatMap(pick -> pick.getSpells().stream()).map(ProPickSpell::getSpellId).distinct().toList()),
                SummonerSpell::getSpellId);
        Map<Long, Item> items = byId(itemRepository.findAllById(picks.stream()
                        .flatMap(pick -> pick.getItems().stream()).map(ProPickItem::getItemId).distinct().toList()),
                Item::getItemId);
        return new TodayPickCatalog(champions, spells, items, runesWithTrees(picks), imageUrls, language);
    }

    private Map<Integer, Rune> runesWithTrees(List<ProPick> picks) {
        Map<Integer, Rune> runes = new HashMap<>(byId(runeRepository.findAllById(picks.stream()
                        .flatMap(pick -> pick.getRunes().stream()).map(ProPickRune::getRuneId).distinct().toList()),
                Rune::runeId));
        List<Integer> treeIds = runes.values().stream()
                .map(Rune::treeId)
                .filter(treeId -> treeId != null && !runes.containsKey(treeId))
                .distinct()
                .toList();
        runes.putAll(byId(runeRepository.findAllById(treeIds), Rune::runeId));
        return runes;
    }

    private <K, T> Map<K, T> byId(List<T> entities, Function<T, K> idOf) {
        return entities.stream().collect(Collectors.toMap(idOf, Function.identity()));
    }
}
