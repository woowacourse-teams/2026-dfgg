package dfgg.application.recommend;

import dfgg.application.champion.ChampionService;
import dfgg.common.CompositionStatsNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CombinationContext;
import dfgg.domain.team.Team;
import dfgg.presentation.dto.ItemDto;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.RecommendationResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 입력한 챔피언 조합을 통계 조회 조건으로 변환하고 추천 빌드를 반환한다.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private final ChampionService championService;
    private final ChampionBuildStatsRepository statsRepository;
    private final RecommendationBuildComposer buildComposer;

    public RecommendationService(
            ChampionService championService,
            ChampionBuildStatsRepository statsRepository,
            RecommendationBuildComposer buildComposer
    ) {
        this.championService = championService;
        this.statsRepository = statsRepository;
        this.buildComposer = buildComposer;
    }

    /**
     * 내 챔피언과 아군·적군 조합에 맞는 아이템 빌드를 추천한다.
     *
     * <p>입력 이름을 챔피언 엔티티로 변환한 뒤 조합의 특징을 분석하고,
     * 해당 조건의 통계를 조회해 슬롯별 대표 아이템을 응답 DTO로 변환한다.
     */
    public RecommendationResponse recommend(RecommendationRequest request) {
        // 요청으로 들어온 챔피언 이름은 ChampionService를 통해 정규 챔피언 데이터로 변환한다.
        Champion myChampion = championService.findChampionByName(request.myChampion().name());
        // 요청의 포지션 문자열은 통계 조회에 사용할 enum 이름으로 변환한다.
        ChampionPosition position = ChampionPosition.valueOf(request.myChampion().position());

        // 아군과 적군도 이름만 가진 요청 객체에서 챔피언 엔티티 목록으로 변환한다.
        Team allies = new Team(request.allies().stream()
                .map(info -> championService.findChampionByName(info.name()))
                .toList());
        Team enemies = new Team(request.enemies().stream()
                .map(info -> championService.findChampionByName(info.name()))
                .toList());

        // 챔피언 목록을 기반으로 적 조합과 아군 조합의 통계 조건을 계산한다.
        CombinationContext combinationContext = CombinationContext.analyze(enemies, allies);

        // 내 챔피언·포지션과 조합 조건이 일치하는 빌드 통계를 조회한다.
        List<ChampionBuildStats> matchingStats = statsRepository.findAllMatchingStats(
                myChampion.getChampionId(),
                position.name(),
                combinationContext.enemyTankHeavy(),
                combinationContext.enemyApHeavy(),
                combinationContext.enemyAssassinHeavy(),
                combinationContext.allyHasMarksman(),
                combinationContext.allyTankHeavy()
        );
        if (matchingStats.isEmpty()) {
            // 해당 챔피언과 포지션에 저장된 통계가 없으면 추천을 만들 수 없다.
            throw new CompositionStatsNotFoundException(myChampion.getName(), position.name());
        }

        // 여러 통계 행을 슬롯별 후보로 합쳐 최종 추천 아이템 순서를 만든다.
        List<Item> bestItems = buildComposer.compose(matchingStats, position);
        if (bestItems.isEmpty()) {
            // 통계 행은 존재하지만 실제 아이템 후보가 없는 경우도 추천 불가로 처리한다.
            throw new CompositionStatsNotFoundException(myChampion.getName(), position.name());
        }
        // 도메인 Item을 외부 응답 전용 DTO로 변환해 내부 모델을 노출하지 않는다.
        List<ItemDto> itemDtos = bestItems.stream()
                .map(ItemDto::from)
                .toList();

        return new RecommendationResponse(
                myChampion.getName(),
                position.name(),
                itemDtos
        );
    }
}
