package dfgg.common.logging;

import java.util.regex.Pattern;

/** 요청 헤더 X-Install-Id. 데스크톱 앱 설치마다 하나인 UUID로, 같은 기기의 요청을 묶는다. */
public record InstallId(String logValue) {

    public static final String HEADER = "X-Install-Id";
    public static final String MDC_KEY = "installId";

    private static final InstallId ABSENT = new InstallId("none");
    private static final InstallId MALFORMED = new InstallId("invalid");
    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    // 사용자가 보낸 값이다. 형식이 틀린 원문을 그대로 남기면 로그 한 줄을 위조할 수 있다.
    public static InstallId from(String header) {
        if (header == null || header.isBlank()) {
            return ABSENT;
        }
        if (!UUID_FORMAT.matcher(header).matches()) {
            return MALFORMED;
        }
        return new InstallId(header);
    }
}
