package dfgg.common.logging;

import java.util.regex.Pattern;

/** 요청 헤더 X-Client-Version. 앱 버전마다 다른 오류를 가르는 데 쓴다. */
public record ClientVersion(String logValue) {

    public static final String HEADER = "X-Client-Version";

    private static final ClientVersion ABSENT = new ClientVersion("none");
    private static final ClientVersion MALFORMED = new ClientVersion("invalid");
    private static final Pattern VERSION_FORMAT = Pattern.compile("^[0-9A-Za-z.+-]{1,32}$");

    // 사용자가 보낸 값이다. 형식이 틀린 원문을 그대로 남기면 로그 한 줄을 위조할 수 있다.
    public static ClientVersion from(String header) {
        if (header == null || header.isBlank()) {
            return ABSENT;
        }
        if (!VERSION_FORMAT.matcher(header).matches()) {
            return MALFORMED;
        }
        return new ClientVersion(header);
    }
}
