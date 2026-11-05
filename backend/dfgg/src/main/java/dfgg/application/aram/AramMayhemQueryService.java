package dfgg.application.aram;

import dfgg.common.exception.AugmentDataUnavailableException;
import dfgg.common.exception.ChampionNotFoundException;
import dfgg.domain.aram.AugmentRepository;
import dfgg.domain.aram.ChampionAugmentRanking;
import dfgg.domain.aram.ChampionAugmentRepository;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.language.Language;
import dfgg.domain.match.PatchVersion;
import dfgg.presentation.dto.AugmentPickRateDto;
import dfgg.presentation.dto.response.ChampionAugmentsResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 가장 높은 패치의 증강 픽률을 응답으로 만든다. 사용자 요청에서 외부 서버를 부르지 않는다. */
@Service
@Transactional(readOnly = true)
public class AramMayhemQueryService {

    private final ChampionRepository championRepository;
    private final AugmentRepository augmentRepository;
    private final ChampionAugmentRepository championAugmentRepository;
    private final ImageUrls imageUrls;

    public AramMayhemQueryService(ChampionRepository championRepository, AugmentRepository augmentRepository,
                                  ChampionAugmentRepository championAugmentRepository, ImageUrls imageUrls) {
        this.championRepository = championRepository;
        this.augmentRepository = augmentRepository;
        this.championAugmentRepository = championAugmentRepository;
        this.imageUrls = imageUrls;
    }

    public ChampionAugmentsResponse findByChampion(int championId, Language language) {
        validateChampion(championId);
        String patch = latestPatch();
        ChampionAugmentRanking ranking = ChampionAugmentRanking.of(
                augmentRepository.findByPatch(patch),
                championAugmentRepository.findByPatchAndChampionId(patch, championId));
        List<AugmentPickRateDto> augments = ranking.augments().stream()
                .map(ranked -> AugmentPickRateDto.of(
                        ranked, imageUrls.augmentOf(patch, ranked.augment().augmentId()), language))
                .toList();
        return new ChampionAugmentsResponse(patch, augments);
    }

    private void validateChampion(int championId) {
        if (!championRepository.existsById((long) championId)) {
            throw new ChampionNotFoundException(String.valueOf(championId));
        }
    }

    private String latestPatch() {
        return PatchVersion.latest(augmentRepository.findDistinctPatches())
                .map(PatchVersion::value)
                .orElseThrow(AugmentDataUnavailableException::new);
    }
}
