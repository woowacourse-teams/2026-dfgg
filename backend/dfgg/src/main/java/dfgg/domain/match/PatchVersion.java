package dfgg.domain.match;

import java.util.Objects;

/**
 * `major.minor` 형식의 패치(예: {@code 16.15}). DB에는 문자열로 저장되지만 순서를 다룰 때는
 * 반드시 이 타입을 거친다.
 *
 * <p>문자열 정렬을 쓰면 안 되는 이유: {@code "16.10".compareTo("16.9") < 0}이라 16.10이
 * 16.9보다 앞선 패치로 뒤집힌다. 최근 N개 패치 윈도를 고르는 집계에서 이 실수는 조용히
 * 잘못된 통계를 만든다.
 */
public record PatchVersion(int major, int minor) implements Comparable<PatchVersion> {

    public static PatchVersion of(String patch) {
        if (patch == null || patch.isBlank()) {
            throw new IllegalArgumentException("패치는 비어있을 수 없습니다.");
        }
        String[] components = patch.split("\\.");
        if (components.length < 2) {
            throw new IllegalArgumentException("패치는 major.minor 형식이어야 합니다: " + patch);
        }
        try {
            return new PatchVersion(
                    Integer.parseInt(components[0].trim()),
                    Integer.parseInt(components[1].trim())
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("패치의 major/minor는 숫자여야 합니다: " + patch, exception);
        }
    }

    /** DB에 저장된 형태 그대로의 문자열. */
    public String value() {
        return major + "." + minor;
    }

    @Override
    public int compareTo(PatchVersion other) {
        Objects.requireNonNull(other, "비교할 패치는 null일 수 없습니다.");
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        return Integer.compare(minor, other.minor);
    }
}
