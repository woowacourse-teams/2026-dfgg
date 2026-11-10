package dfgg.application.aram;

import dfgg.common.exception.AugmentDataUnavailableException;
import dfgg.common.exception.AugmentPatchNotFoundException;
import dfgg.common.exception.InvalidPatchException;
import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentRepository;
import dfgg.domain.language.Language;
import dfgg.domain.match.PatchVersion;
import dfgg.infrastructure.image.AugmentTitleImage;
import dfgg.infrastructure.image.AugmentTitleRenderer;
import dfgg.presentation.dto.AugmentTitleDto;
import dfgg.presentation.dto.response.AugmentTitlesResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱이 게임 카드 제목과 그림으로 비교할 견본을 패치·언어별로 만든다.
 * <p>
 * 챔피언과 상관없이 그 패치의 증강 전체를 그린다. 앱은 어느 챔피언 판이든 같은 견본으로 판별한다.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AugmentTitleService {

    private static final Logger log = LoggerFactory.getLogger(AugmentTitleService.class);

    private final AugmentRepository augmentRepository;
    private final AugmentTitleRenderer renderer;

    public AugmentTitleService(AugmentRepository augmentRepository, AugmentTitleRenderer renderer) {
        this.augmentRepository = augmentRepository;
        this.renderer = renderer;
    }

    /** {@code patch}가 없으면 저장된 최고 패치다. 요청 언어 이름이 없는 증강은 다른 언어로 대신 그리지 않는다. */
    public AugmentTitlesResponse findTitles(String patch, Language language) {
        String resolvedPatch = resolve(patch);
        List<Augment> augments = augmentRepository.findByPatch(resolvedPatch);
        if (augments.isEmpty()) {
            throw new AugmentPatchNotFoundException(resolvedPatch);
        }
        List<AugmentTitleDto> images = augments.stream()
                .sorted(Comparator.comparingInt(Augment::augmentId))
                .flatMap(augment -> titleOf(augment, language).stream())
                .toList();
        return AugmentTitlesResponse.of(resolvedPatch, language, renderer.style(), images);
    }

    private Optional<AugmentTitleDto> titleOf(Augment augment, Language language) {
        return augment.exactNameIn(language)
                .flatMap(name -> draw(augment.augmentId(), name));
    }

    private Optional<AugmentTitleDto> draw(int augmentId, String name) {
        Optional<AugmentTitleImage> image = renderer.render(name);
        if (image.isEmpty()) {
            log.warn("폰트가 그리지 못하는 글자가 있어 제목 견본에서 뺍니다: augmentId={}, name={}", augmentId, name);
        }
        return image.map(drawn -> AugmentTitleDto.of(augmentId, name, drawn));
    }

    private String resolve(String patch) {
        if (patch == null) {
            return latestPatch();
        }
        try {
            return PatchVersion.of(patch).value();
        } catch (IllegalArgumentException exception) {
            throw new InvalidPatchException(patch, exception);
        }
    }

    private String latestPatch() {
        return PatchVersion.latest(augmentRepository.findDistinctPatches())
                .map(PatchVersion::value)
                .orElseThrow(AugmentDataUnavailableException::new);
    }
}
