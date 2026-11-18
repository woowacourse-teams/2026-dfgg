package dfgg.application.rune;

import dfgg.domain.match.PatchVersion;
import dfgg.domain.rune.NormalizedMatchParticipantRuneRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 룬 추천에 쓸 패치 후보. 최신 패치부터 보고, 기록이 부족하면 다음 후보로 넘어간다.
 *
 * <p>설정({@code recommendation.rune.patch})으로 고정하면 그 패치만 본다.
 * 룬 기록 전체를 훑어야 해서 처음 쓰일 때 한 번만 계산하고 이후엔 메모리 값을 준다. 새 패치로 넘어가려면 재시작한다.
 */
@Component
public class RuneRecommendationPatch {

    private final NormalizedMatchParticipantRuneRepository runeRepository;
    private final String pinnedPatch;
    private final int patchWindow;

    private volatile List<String> loaded;

    public RuneRecommendationPatch(NormalizedMatchParticipantRuneRepository runeRepository,
                                   @Value("${recommendation.rune.patch:}") String pinnedPatch,
                                   @Value("${recommendation.rune.patch-window:3}") int patchWindow) {
        this.runeRepository = runeRepository;
        this.pinnedPatch = normalize(pinnedPatch);
        this.patchWindow = patchWindow;
    }

    private static String normalize(String pinnedPatch) {
        if (pinnedPatch == null || pinnedPatch.isBlank()) {
            return null;
        }
        return PatchVersion.of(pinnedPatch).value();
    }

    /** 높은 패치 순. 룬 기록이 아직 없으면 비어 있고, 비어 있는 결과는 기억하지 않고 다음 요청에서 다시 확인한다. */
    public List<String> candidates() {
        if (pinnedPatch != null) {
            return List.of(pinnedPatch);
        }
        List<String> patches = loaded;
        if (patches != null) {
            return patches;
        }
        return load();
    }

    // 첫 요청이 몰려도 계산은 한 번이다. 기다린 요청은 먼저 계산된 값을 받는다.
    private synchronized List<String> load() {
        if (loaded != null) {
            return loaded;
        }
        List<String> latest = runeRepository.findDistinctPatches().stream()
                .map(PatchVersion::of)
                .sorted(Comparator.reverseOrder())
                .limit(patchWindow)
                .map(PatchVersion::value)
                .toList();
        if (!latest.isEmpty()) {
            loaded = latest;
        }
        return latest;
    }
}
