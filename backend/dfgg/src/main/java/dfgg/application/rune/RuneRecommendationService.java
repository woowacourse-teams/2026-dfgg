package dfgg.application.rune;

import static java.util.stream.Collectors.toMap;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.champion.ChampionService;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.language.Language;
import dfgg.domain.rune.NormalizedMatchParticipantRune;
import dfgg.domain.rune.NormalizedMatchParticipantRuneRepository;
import dfgg.domain.rune.Rune;
import dfgg.domain.rune.RunePage;
import dfgg.domain.rune.RunePageSamples;
import dfgg.domain.rune.RuneRecommendation;
import dfgg.domain.rune.RuneRecommendationPolicy;
import dfgg.domain.rune.RuneRepository;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendedRunePageDto;
import dfgg.presentation.dto.RuneDto;
import dfgg.presentation.dto.response.RuneRecommendationResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챔피언·포지션의 추천 패치 룬 기록에서 페이지를 골라, 이름과 이미지를 붙여 내려준다.
 * 이름은 룬 카탈로그에서, 이미지는 추천 패치 폴더에서 가져오므로 Data Dragon을 부르지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class RuneRecommendationService {

    private final ChampionService champions;
    private final ChampionPositionNormalizer positions;
    private final RuneRecommendationPatch recommendationPatch;
    private final NormalizedMatchParticipantRuneRepository participantRunes;
    private final RuneRepository runes;
    private final ImageUrls imageUrls;
    private final RuneRecommendationPolicy policy;

    public RuneRecommendationService(
            ChampionService champions,
            ChampionPositionNormalizer positions,
            RuneRecommendationPatch recommendationPatch,
            NormalizedMatchParticipantRuneRepository participantRunes,
            RuneRepository runes,
            ImageUrls imageUrls,
            @Value("${recommendation.rune.minimum-games}") int minimumGames,
            @Value("${recommendation.rune.minimum-path-games}") int minimumPathGames,
            @Value("${recommendation.rune.page-count}") int pageCount
    ) {
        this.champions = champions;
        this.positions = positions;
        this.recommendationPatch = recommendationPatch;
        this.participantRunes = participantRunes;
        this.runes = runes;
        this.imageUrls = imageUrls;
        this.policy = new RuneRecommendationPolicy(minimumGames, minimumPathGames, pageCount);
    }

    public RuneRecommendationResponse recommend(ChampionDto myChampion, Language language) {
        Champion champion = champions.findChampionByName(myChampion.name());
        ChampionPosition position = ChampionPosition.valueOf(myChampion.position());
        return recommendationPatch.current()
                .map(patch -> recommend(champion, position, patch, language))
                .orElseGet(RuneRecommendationResponse::withoutPatch);
    }

    private RuneRecommendationResponse recommend(Champion champion, ChampionPosition position, String patch,
                                                 Language language) {
        List<RunePage> pages = participantRunes.findByChampionIdAndPositionInAndPatch(
                        Math.toIntExact(champion.getChampionId()), positions.riotValuesOf(position), patch)
                .stream()
                .map(NormalizedMatchParticipantRune::runePage)
                .toList();
        RuneRecommendation recommendation = new RunePageSamples(pages).recommend(policy);
        if (recommendation.pages().isEmpty()) {
            return new RuneRecommendationResponse(patch, recommendation.games(), List.of());
        }
        // 카탈로그는 계열·룬·파편을 합쳐 백 개가 안 되므로 통째로 읽는다.
        Map<Integer, Rune> catalog = runes.findAll().stream().collect(toMap(Rune::runeId, Function.identity()));
        List<RecommendedRunePageDto> recommended = recommendation.pages().stream()
                .map(page -> RecommendedRunePageDto.of(page, runeId -> rune(runeId, catalog, patch, language)))
                .toList();
        return new RuneRecommendationResponse(patch, recommendation.games(), recommended);
    }

    private RuneDto rune(int runeId, Map<Integer, Rune> catalog, String patch, Language language) {
        Rune rune = catalog.get(runeId);
        if (rune == null) {
            return RuneDto.unknown(runeId);
        }
        return new RuneDto(runeId, rune.nameIn(language), imageUrls.runeOf(patch, runeId));
    }
}
