package dfgg.application.recommend.v3.generator.transition;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.recommend.v3.generator.RecentPatchWindowCache;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RecentPatchWindow;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 원본 참가자 테이블에서 Build 전이를 바로 집계한다.
 */
public class DatabaseBuildTransitionReader implements BuildTransitionReader {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final ChampionPositionNormalizer positionNormalizer;
    private final RecentPatchWindowCache recentPatchWindow;

    public DatabaseBuildTransitionReader(
            NormalizedMatchParticipantRepository participantRepository,
            ChampionPositionNormalizer positionNormalizer,
            RecentPatchWindowCache recentPatchWindow
    ) {
        this.participantRepository = participantRepository;
        this.positionNormalizer = positionNormalizer;
        this.recentPatchWindow = recentPatchWindow;
    }

    @Override
    public List<TransitionRow> exactPrefix(BuildTransitionRequest request) {
        String prefix = request.purchasedItemIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return toRows(participantRepository.findNextItemAfterExactPrefix(
                request.championId(), riotPositions(request), prefix,
                request.nextPosition(), recentPatches(), coreItemIdTexts(request)
        ));
    }

    @Override
    public List<TransitionRow> lastItem(BuildTransitionRequest request) {
        return toRows(participantRepository.findNextItemAfterLastItem(
                request.championId(), riotPositions(request), request.lastItemId(),
                recentPatches(), coreItemIdTexts(request)
        ));
    }

    private List<TransitionRow> toRows(List<Object[]> rows) {
        return rows.stream().map(TransitionRow::from).toList();
    }

    private Collection<String> recentPatches() {
        RecentPatchWindow window = recentPatchWindow.current();
        if (window.isEmpty()) {
            return List.of("");
        }
        return window.patches();
    }

    private List<String> riotPositions(BuildTransitionRequest request) {
        return positionNormalizer.riotValuesOf(request.position());
    }

    /** 구매 순서 쿼리는 아이템 ID를 문자열로 다룬다. */
    private List<String> coreItemIdTexts(BuildTransitionRequest request) {
        return request.coreItemIds().stream().map(String::valueOf).toList();
    }
}
