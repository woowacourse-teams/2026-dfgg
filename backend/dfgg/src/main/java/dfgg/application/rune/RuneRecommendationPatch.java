package dfgg.application.rune;

import dfgg.domain.match.PatchVersion;
import dfgg.domain.rune.NormalizedMatchParticipantRuneRepository;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 룬 추천에 쓸 패치. 통계와 룬 이미지가 모두 이 패치를 본다.
 *
 * <p>설정({@code recommendation.rune.patch})으로 고정하지 않으면 룬 기록의 가장 높은 패치를 쓴다.
 * 룬 기록 전체를 훑어야 해서 처음 쓰일 때 한 번만 계산하고 이후엔 메모리 값을 준다. 새 패치로 넘어가려면 재시작한다.
 */
@Component
public class RuneRecommendationPatch {

    private final NormalizedMatchParticipantRuneRepository runeRepository;
    private final String pinnedPatch;

    private volatile String loaded;

    public RuneRecommendationPatch(NormalizedMatchParticipantRuneRepository runeRepository,
                          @Value("${recommendation.rune.patch:}") String pinnedPatch) {
        this.runeRepository = runeRepository;
        this.pinnedPatch = pinnedPatch == null || pinnedPatch.isBlank() ? null : PatchVersion.of(pinnedPatch).value();
    }

    /** 룬 기록이 아직 없으면 비어 있다. 비어 있는 결과는 기억하지 않고 다음 요청에서 다시 확인한다. */
    public Optional<String> current() {
        if (pinnedPatch != null) {
            return Optional.of(pinnedPatch);
        }
        String patch = loaded;
        if (patch != null) {
            return Optional.of(patch);
        }
        return load();
    }

    // 첫 요청이 몰려도 계산은 한 번이다. 기다린 요청은 먼저 계산된 값을 받는다.
    private synchronized Optional<String> load() {
        if (loaded != null) {
            return Optional.of(loaded);
        }
        Optional<String> latest = runeRepository.findDistinctPatches().stream()
                .map(PatchVersion::of)
                .max(Comparator.naturalOrder())
                .map(PatchVersion::value);
        latest.ifPresent(patch -> loaded = patch);
        return latest;
    }
}
