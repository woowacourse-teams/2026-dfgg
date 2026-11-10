package dfgg.application.patch;

import dfgg.domain.match.PatchVersion;
import java.util.Objects;

/**
 * 지금 서비스가 기준으로 삼는 패치. 응답 이미지 URL의 버전 폴더와 추천 기본 패치가 여기서 읽는다.
 * 실행 중에 {@link PatchMonitor}가 앞으로만 바꾼다.
 */
public class ServicePatch {

    private volatile PatchVersion current;

    public ServicePatch(PatchVersion initial) {
        this.current = Objects.requireNonNull(initial, "초기 서비스 패치는 null일 수 없습니다.");
    }

    public PatchVersion current() {
        return current;
    }

    public String value() {
        return current.value();
    }

    /** 더 높은 패치일 때만 바꾼다. 바뀌었으면 참이다. */
    public synchronized boolean advanceTo(PatchVersion next) {
        if (next.compareTo(current) <= 0) {
            return false;
        }
        current = next;
        return true;
    }
}
