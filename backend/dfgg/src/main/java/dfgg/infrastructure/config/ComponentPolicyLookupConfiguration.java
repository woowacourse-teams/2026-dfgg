package dfgg.infrastructure.config;

import dfgg.domain.match.ComponentPolicyLookup;
import dfgg.infrastructure.recommend.ComponentPolicyLookupLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class ComponentPolicyLookupConfiguration {
    @Bean
    @Lazy
    public ComponentPolicyLookup componentPolicyLookup(@Value("${recommendation.component-ltr.policy-lookup-path:}") String path,
                                                       ResourceLoader resources) {
        if (path.isBlank()) return ComponentPolicyLookup.disabled();
        try (var input = resources.getResource(path).getInputStream()) {
            return ComponentPolicyLookupLoader.load(input.readAllBytes());
        } catch (IOException exception) { throw new UncheckedIOException("정책 조회표 로딩 실패", exception); }
    }
}
