package dfgg.application.aram;

import dfgg.common.exception.AugmentDataUnavailableException;
import dfgg.common.exception.ChampionNotFoundException;
import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentNameLookup;
import dfgg.domain.aram.AugmentRepository;
import dfgg.domain.aram.ChampionAugmentRanking;
import dfgg.domain.aram.ChampionAugmentRepository;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.language.Language;
import dfgg.domain.match.PatchVersion;
import dfgg.presentation.dto.AugmentPickRateDto;
import dfgg.presentation.dto.response.AugmentLookupResponse;
import dfgg.presentation.dto.response.ChampionAugmentsResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 가장 높은 패치의 증강 픽률을 응답으로 만든다. 사용자 요청에서 외부 서버를 부르지 않는다.
 * <p>
 * 증강과 픽률을 나눠 읽으므로 그 사이에 재수집이 커밋되면 서로 다른 시점을 보게 된다. 한 시점으로 읽도록 격리 수준을 올린다.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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

    /** 화면에서 읽은 후보의 픽률을 준다. 후보 중 하나를 고르라는 뜻이 되지 않게 받은 순서를 유지한다. */
    public AugmentLookupResponse lookup(int championId, List<String> names, Language language) {
        validateChampion(championId);
        String patch = latestPatch();
        List<Augment> catalog = augmentRepository.findByPatch(patch);
        ChampionAugmentRanking ranking = ChampionAugmentRanking.of(
                catalog, championAugmentRepository.findByPatchAndChampionId(patch, championId));
        AugmentNameLookup lookup = AugmentNameLookup.of(catalog, names);
        List<AugmentPickRateDto> augments = lookup.augments().stream()
                .map(augment -> pickRateOf(augment, ranking, patch, language))
                .toList();
        return new AugmentLookupResponse(patch, augments, lookup.unmatchedNames());
    }

    private AugmentPickRateDto pickRateOf(Augment augment, ChampionAugmentRanking ranking, String patch,
                                          Language language) {
        String iconUrl = imageUrls.augmentOf(patch, augment.augmentId());
        return ranking.find(augment.augmentId())
                .map(ranked -> AugmentPickRateDto.of(ranked, iconUrl, language))
                .orElseGet(() -> AugmentPickRateDto.unranked(augment, iconUrl, language));
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
