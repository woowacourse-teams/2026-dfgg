package dfgg.common.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RequestLoggingConfiguration {

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter(
            @Value("${logging.slow-request-threshold-millis:1000}") long slowRequestMillis) {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter(slowRequestMillis));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
