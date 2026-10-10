package dfgg.infrastructure.config;

import dfgg.domain.match.PreparationLookup;
import dfgg.infrastructure.recommend.PreparationLookupLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class PreparationLookupConfiguration {
    @Bean
    public PreparationLookup preparationLookup(@Value("${recommendation.component-ltr.preparation-lookup-path:}") String path,
                                               ResourceLoader resources) {
        if (path.isBlank()) return PreparationLookup.disabled();
        try (var input = resources.getResource(path).getInputStream()) {
            return PreparationLookupLoader.load(input.readAllBytes());
        } catch (IOException exception) { throw new UncheckedIOException("Preparation Lookup 로딩 실패", exception); }
    }
}
