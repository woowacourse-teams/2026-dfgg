package dfgg.application.recommend.fallback;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 폴백 체인의 최종 안전망. 조합·상황을 전부 무시하고 그 챔피언·포지션이 가장 많이 산
 * 빌드를 그대로 돌려준다.
 */
@Component
public class MostFrequentBuildStrategy implements RecommendationStrategy {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final ChampionPositionNormalizer positionNormalizer;

    public MostFrequentBuildStrategy(
            NormalizedMatchParticipantRepository participantRepository,
            ChampionPositionNormalizer positionNormalizer
    ) {
        this.participantRepository = participantRepository;
        this.positionNormalizer = positionNormalizer;
    }

    @Override
    public FallbackStage stage() {
        return FallbackStage.MOST_FREQUENT_BUILD;
    }

    @Override
    public Optional<List<Long>> recommend(RecommendationContext context) {
        List<String> positions = positionNormalizer.riotValuesOf(context.position());
        return participantRepository.findMostFrequentBuild(context.myChampionId(), positions)
                .map(this::parseItemIds)
                .flatMap(build -> NextItemSelector.pick(build, context.purchasedItemIds().size()));
    }

    private List<Long> parseItemIds(String commaSeparatedItemIds) {
        return Arrays.stream(commaSeparatedItemIds.split(","))
                .map(Long::valueOf)
                .toList();
    }
}
