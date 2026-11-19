package dfgg.common.logging;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 본문을 남길 경로(접두어)와 본문 하나의 최대 바이트. */
@ConfigurationProperties("logging.access")
public record AccessLogProperties(List<String> bodyPaths, int maxBodyBytes) {

    public static final AccessLogProperties NO_BODY = new AccessLogProperties(List.of(), 0);

    public AccessLogProperties {
        bodyPaths = copyOrEmpty(bodyPaths);
    }

    public boolean recordsBody(String uri) {
        return bodyPaths.stream().anyMatch(uri::startsWith);
    }

    // 설정이 없으면 본문을 남기지 않는다.
    private static List<String> copyOrEmpty(List<String> paths) {
        if (paths == null) {
            return List.of();
        }
        return List.copyOf(paths);
    }
}
