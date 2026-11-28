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
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 요청 한 건을 로그 한 줄로 남긴다.
 * <p>
 * 본문 기록 경로면 요청·응답 본문을 {@code req}·{@code res} key-value로 붙인다.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String ACTUATOR_PREFIX = "/actuator";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TRACE_ID_BYTES = 4;

    private final long slowRequestMillis;
    private final AccessLogProperties accessLog;

    public RequestLoggingFilter(long slowRequestMillis) {
        this(slowRequestMillis, AccessLogProperties.NO_BODY);
    }

    public RequestLoggingFilter(long slowRequestMillis, AccessLogProperties accessLog) {
        this.slowRequestMillis = slowRequestMillis;
        this.accessLog = accessLog;
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
        if (accessLog.recordsBody(request.getRequestURI())) {
            filterRecordingBody(request, response, chain);
            return;
        }
        filterAndRecord(request, response, chain);
    }

    private void filterRecordingBody(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 한 바이트 더 담아 두어야 상한을 넘었는지 알 수 있다.
        ContentCachingRequestWrapper cachingRequest =
                new ContentCachingRequestWrapper(request, accessLog.maxBodyBytes() + 1);
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        try {
            filterAndRecord(cachingRequest, cachingResponse, chain);
        } finally {
            // 빠뜨리면 클라이언트가 빈 응답을 받는다.
            cachingResponse.copyBodyToResponse();
        }
    }

    private void filterAndRecord(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = newTraceId();
        MDC.put(TRACE_ID, traceId);
        MDC.put(InstallId.MDC_KEY, InstallId.from(request.getHeader(InstallId.HEADER)).logValue());
        response.setHeader(TRACE_ID_HEADER, traceId);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
            record(request, response, response.getStatus(), elapsedMillis(startedAt));
        } catch (Exception exception) {
            // 여기서 남기지 않으면 어디서 남는지가 서블릿 컨테이너에 달린다. 추적 번호도 붙지 않는다.
            log.error("{} 처리 중 예외", describe(request), exception);
            record(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, elapsedMillis(startedAt));
            throw exception;
        } finally {
            // 스레드는 재사용된다. 비우지 않으면 다음 요청이 남의 추적 번호를 달고 나간다.
            MDC.remove(TRACE_ID);
            MDC.remove(InstallId.MDC_KEY);
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response, int status, long durationMillis) {
        LoggingEventBuilder event = levelFor(durationMillis)
                .setMessage("{} status={} duration_ms={}")
                .addArgument(describe(request))
                .addArgument(status)
                .addArgument(durationMillis)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", status)
                .addKeyValue("duration_ms", durationMillis)
                .addKeyValue("clientVersion", ClientVersion.from(request.getHeader(ClientVersion.HEADER)).logValue());
        addBodies(event, request, response);
        event.log();
    }

    // 느린 요청이 정상 요청 사이에 묻히면 사후에 찾아내지 못한다.
    private LoggingEventBuilder levelFor(long durationMillis) {
        if (durationMillis >= slowRequestMillis) {
            return log.atWarn();
        }
        return log.atInfo();
    }

    private void addBodies(LoggingEventBuilder event, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof ContentCachingRequestWrapper cachingRequest) {
            LoggedBody.of(cachingRequest.getContentAsByteArray(), accessLog.maxBodyBytes()).addTo(event, "req");
        }
        if (response instanceof ContentCachingResponseWrapper cachingResponse) {
            LoggedBody.of(cachingResponse.getContentAsByteArray(), accessLog.maxBodyBytes()).addTo(event, "res");
        }
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
