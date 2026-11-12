package dfgg.infrastructure.config;

import dfgg.domain.item.ItemExclusionGroups;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 손으로 관리하는 아이템 규칙을 빈으로 등록한다. 도메인 클래스가 Spring에 의존하지 않도록
 * {@code BuildPolicyConfiguration}과 같은 방식을 따른다.
 */
@Configuration(proxyBeanMethods = false)
public class ItemCatalogConfiguration {

    @Bean
    ItemExclusionGroups itemExclusionGroups() {
        return new ItemExclusionGroups();
    }
}
