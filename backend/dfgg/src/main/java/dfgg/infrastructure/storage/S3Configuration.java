package dfgg.infrastructure.storage;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Configuration {
    @Bean
    @Lazy
    public S3Client s3Client(@Value("${assets.s3.region:ap-northeast-2}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(builder -> builder
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
    }
}
