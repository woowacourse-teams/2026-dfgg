package dfgg.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 한 건을 로그 한 줄로 남긴다.
 * <p>
 * 형식은 CloudWatch Logs Insights의 {@code parse}와 한 쌍이다.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String ACTUATOR_PREFIX = "/actuator";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TRACE_ID_BYTES = 4;

    private final long slowRequestMillis;

    public RequestLoggingFilter(long slowRequestMillis) {
        this.slowRequestMillis = slowRequestMillis;
    }

    /**
     * Prometheus가 30초마다 긁어간다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ACTUATOR_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = newTraceId();
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
            record(request, response.getStatus(), elapsedMillis(startedAt));
        } catch (Exception exception) {
            // 여기서 남기지 않으면 어디서 남는지가 서블릿 컨테이너에 달린다. 추적 번호도 붙지 않는다.
            log.error("{} 처리 중 예외", describe(request), exception);
            record(request, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, elapsedMillis(startedAt));
            throw exception;
        } finally {
            // 스레드는 재사용된다. 비우지 않으면 다음 요청이 남의 추적 번호를 달고 나간다.
            MDC.remove(TRACE_ID);
        }
    }

    private void record(HttpServletRequest request, int status, long durationMillis) {
        String line = "{} status={} duration_ms={}";
        if (durationMillis >= slowRequestMillis) {
            // 느린 요청이 정상 요청 사이에 묻히면 사후에 찾아내지 못한다.
            log.warn(line, describe(request), status, durationMillis);
            return;
        }
        log.info(line, describe(request), status, durationMillis);
    }

    private String describe(HttpServletRequest request) {
        return "method=" + request.getMethod() + " uri=" + request.getRequestURI();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String newTraceId() {
        byte[] bytes = new byte[TRACE_ID_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
