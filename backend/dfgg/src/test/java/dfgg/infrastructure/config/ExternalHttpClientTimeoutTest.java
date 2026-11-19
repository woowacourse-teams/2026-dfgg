package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dfgg.infrastructure.external.client.RiotClient;
import dfgg.infrastructure.external.config.RiotApiProperties;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class ExternalHttpClientTimeoutTest {

    private static final Duration SLOW_RESPONSE = Duration.ofSeconds(2);

    @Test
    void application_yml의_외부_호출_timeout을_HTTP_클라이언트_설정으로_bind한다() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class))
                .run(context -> {
                    HttpClientSettings settings = context.getBean(HttpClientSettings.class);
                    assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(10));
                });
    }

    @Test
    void Boot가_주입한_RestClient_Builder로_만든_Riot_클라이언트는_응답이_늦으면_read_timeout으로_끊는다()
            throws Exception {
        ExecutorService handlers = Executors.newCachedThreadPool();
        HttpServer slowRiot = slowServer(handlers);
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            HttpClientAutoConfiguration.class,
                            ImperativeHttpClientAutoConfiguration.class,
                            RestClientAutoConfiguration.class
                    ))
                    .withPropertyValues("spring.http.clients.read-timeout=200ms")
                    .run(context -> {
                        RiotClient client = new RiotClient(context.getBean(RestClient.Builder.class),
                                riotProperties(slowRiot));
                        long startedAt = System.nanoTime();

                        assertThatThrownBy(() -> client.getRawMatch("KR_1"))
                                .isInstanceOf(ResourceAccessException.class);
                        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(SLOW_RESPONSE);
                    });
        } finally {
            slowRiot.stop(0);
            handlers.shutdownNow();
        }
    }

    private HttpServer slowServer(ExecutorService handlers) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(SLOW_RESPONSE.toMillis());
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(handlers);
        server.start();
        return server;
    }

    private RiotApiProperties riotProperties(HttpServer server) {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new RiotApiProperties("test-api-key", baseUrl, baseUrl, 18, 95);
    }
}
