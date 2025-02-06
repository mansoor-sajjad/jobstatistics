package no.nav.jobsearch;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

  /**
   * Creates a RestTemplate bean to be used for making HTTP requests.
   *
   * @return A RestTemplate bean
   */
  @Bean
  public RestTemplate restTemplate() {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.setInterceptors(List.of(new UriEncodingInterceptor()));
    return restTemplate;
  }
}
