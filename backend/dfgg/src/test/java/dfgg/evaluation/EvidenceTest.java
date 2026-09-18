package dfgg.evaluation;

import dfgg.application.champion.ChampionService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.CandidateTopK;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.explanation.ChampionDirectory;
import dfgg.application.recommend.v3.explanation.ChampionProfile;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class EvidenceTest {

    @Autowired private NextItemRecommendationService service;
    @Autowired private ChampionService championService;
    @Autowired private ChampionRepository championRepository;
    @Autowired private List<CandidateGenerator> generators;
    @Autowired private CandidateTopK candidateTopK;

    private long idOf(String riotKey) {
        return championService.findChampionByName(riotKey).getChampionId();
    }

    @Test
    void probe() {
        List<String> allies = List.of("LeeSin", "Ahri", "Ezreal", "Karma");
        List<String> enemies = List.of("Darius", "Graves", "Viktor", "Jinx", "Lulu");

        NextItemRecommendationRequest request = new NextItemRecommendationRequest(
                new ChampionDto("Renekton", "TOP"), List.of(),
                allies.stream().map(name -> new ChampionDto(name, "MID")).toList(),
                enemies.stream().map(name -> new ChampionDto(name, "MID")).toList(),
                "PLATINUM", "16.16");
        NextItemRecommendationResponse response = service.recommendNextItem(request);

        RecommendationQuery query = new RecommendationQuery(
                idOf("Renekton"), ChampionPosition.TOP, List.of(),
                allies.stream().map(this::idOf).toList(),
                enemies.stream().map(this::idOf).toList(),
                "PLATINUM", "16.16");

        List<GeneratorResult> results = new ArrayList<>();
        for (CandidateGenerator generator : generators) {
            results.add(generator.generate(query, candidateTopK.of(generator.source())));
        }
        CandidateUnion union = CandidateUnion.merge(results);

        ChampionDirectory directory = new ChampionDirectory(championRepository);
        Map<Long, ChampionProfile> profiles = directory.resolve(
                java.util.stream.Stream.concat(query.allyChampionIds().stream(),
                        query.enemyChampionIds().stream()).toList());

        System.out.println("CHECK>>> ===== 레넥톤 TOP =====");
        response.recommendedItems().forEach(item -> {
            System.out.println("CHECK>>> " + item.name() + " | " + item.description());
            for (CandidateSource source : List.of(CandidateSource.COUNTER, CandidateSource.ALLY_SYNERGY)) {
                union.candidateOf(item.id()).evidenceOf(source).ifPresent(evidence -> {
                    if (evidence.scoreByChampionId().isEmpty()) {
                        return;
                    }
                    String named = evidence.scoreByChampionId().entrySet().stream()
                            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                            .map(e -> "%s %.2f".formatted(
                                    profiles.containsKey(e.getKey())
                                            ? profiles.get(e.getKey()).name() : e.getKey(),
                                    e.getValue()))
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    System.out.println("CHECK>>>     " + source + ": " + named);
                });
            }
        });
    }
}
