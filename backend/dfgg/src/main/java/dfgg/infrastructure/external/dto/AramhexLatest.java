package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dfgg.common.exception.AugmentDataRejectedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/** aramhex-data의 최신 패치 안내. 패치 형식과 파일 해시를 스스로 검증한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AramhexLatest(
        String patch,
        String mode,
        @JsonProperty("schema_version") int schemaVersion,
        String path,
        Provisional provisional,
        Map<String, FileEntry> files
) {

    private static final Pattern PATCH_FORMAT = Pattern.compile("(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,3})");
    private static final String EXPECTED_MODE = "normal";
    private static final int EXPECTED_SCHEMA_VERSION = 1;

    public AramhexLatest {
        files = files == null ? Map.of() : Map.copyOf(files);
    }

    /** 패치는 파일 주소와 S3 키에 들어가는 외부 값이다. {@code PatchVersion}과 표기가 어긋나지 않게 앞자리 0도 막는다. */
    public void validate() {
        if (patch == null || !PATCH_FORMAT.matcher(patch).matches()) {
            throw new AugmentDataRejectedException("aramhex-data 패치 형식이 다릅니다: " + patch);
        }
        if (!EXPECTED_MODE.equals(mode)) {
            throw new AugmentDataRejectedException("aramhex-data mode가 다릅니다: " + mode);
        }
        if (schemaVersion != EXPECTED_SCHEMA_VERSION) {
            throw new AugmentDataRejectedException("aramhex-data schema_version이 바뀌었습니다: " + schemaVersion);
        }
        if (!("patches/" + patch + "/normal").equals(path)) {
            throw new AugmentDataRejectedException("aramhex-data path가 패치의 정식 폴더가 아닙니다.");
        }
    }

    /** 임시 증강이 없는 스냅샷은 이 값이 비어 있다. */
    public int provisionalAugmentCount() {
        return provisional == null ? 0 : provisional.augments();
    }

    public void verify(String file, byte[] content) {
        FileEntry entry = files.get(file);
        if (entry == null) {
            throw new AugmentDataRejectedException("aramhex-data 해시 목록에 파일이 없습니다: " + file);
        }
        if (!entry.matches(content)) {
            throw new AugmentDataRejectedException("aramhex-data SHA-256이 다릅니다: " + file);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Provisional(int augments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileEntry(String sha256) {

        private boolean matches(byte[] content) {
            return hashOf(content).equalsIgnoreCase(sha256);
        }

        private static String hashOf(byte[] content) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
            }
        }
    }
}
