package dfgg.infrastructure.external.config;

import dfgg.infrastructure.external.client.GeminiClient;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 타임아웃은 여기서 건다. 클라이언트 생성자에서 request factory를 바꾸면 테스트의 MockRestServiceServer를 덮어쓴다.
 */
@Configuration
public class GeminiClientConfiguration {

    @Bean
    public GeminiClient geminiClient(RestClient.Builder builder, GeminiProperties properties) {
        return new GeminiClient(builder.clone().requestFactory(timeoutRequestFactory(properties)), properties);
    }

    private JdkClientHttpRequestFactory timeoutRequestFactory(GeminiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return requestFactory;
    }
}
