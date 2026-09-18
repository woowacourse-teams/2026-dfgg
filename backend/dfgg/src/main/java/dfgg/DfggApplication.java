package dfgg;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DfggApplication {

	@Bean
	JsonMapper objectMapper() {
		return JsonMapper.builder().build();
	}

	public static void main(String[] args) {
		SpringApplication.run(DfggApplication.class, args);
	}

}
