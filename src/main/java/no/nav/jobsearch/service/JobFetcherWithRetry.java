package no.nav.jobsearch.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import no.nav.jobsearch.model.JobFeedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class JobFetcherWithRetry {

  private static final Logger logger = LoggerFactory.getLogger(
    JobFetcherWithRetry.class
  );

  @Value("${api.token}")
  private String token;

  @Value("${feed.api.url}")
  private String apiUrl;

  @Value("${feed.batch.size:100}")
  private int batchSize;

  private final RestTemplate restTemplate;

  public JobFetcherWithRetry(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  /**
   * Fetches data from the API with retry logic.
   * The method retries fetching data from the API if a ResourceAccessException or HttpClientErrorException occurs.
   * The method retries fetching data up to the maximum number of attempts defined by the configuration.
   * The method uses exponential backoff with a multiplier to delay retries.
   *
   * @param now The current date and time
   * @param oldestUpdatedDate The oldest updated date
   * @param newestUpdatedDate The newest updated date
   * @param pageNumber The page number
   * @return The response entity containing the fetched data
   */
  @Retryable(
    retryFor = {
      ResourceAccessException.class, HttpClientErrorException.class,
    },
    maxAttemptsExpression = "${retry.maxAttempts:3}",
    backoff = @Backoff(
      delayExpression = "${retry.maxDelay:5000}",
      multiplierExpression = "${retry.backoff.multiplier:3}"
    )
  )
  public ResponseEntity<JobFeedResponse> fetchDataWithRetry(
    LocalDateTime now,
    LocalDateTime oldestUpdatedDate,
    LocalDateTime newestUpdatedDate,
    int pageNumber
  ) {
    String url = buildApiUrl(
      now,
      oldestUpdatedDate,
      newestUpdatedDate,
      pageNumber
    );

    try {
      logger.info("Fetching data from URL: {}", url);
      return restTemplate.exchange(
        url,
        HttpMethod.GET,
        getHttpEntity(),
        JobFeedResponse.class
      );
    } catch (ResourceAccessException e) {
      logger.error("Timeout occurred while fetching data from URL: {}", url, e);
      throw e;
    } catch (HttpClientErrorException e) {
      logger.error(
        "HTTP error occurred while fetching data from URL: {}",
        url,
        e
      );
      throw e;
    } catch (Exception e) {
      logger.error(
        "Unexpected error occurred while fetching data from URL: {}",
        url,
        e
      );
      throw new RuntimeException("Failed to fetch data after retries", e);
    }
  }

  /**
   * Creates an HTTP entity with the authorization header.
   *
   * @return The HTTP entity with the authorization header
   */
  HttpEntity<String> getHttpEntity() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + token);
    headers.set("Accept", "application/json");
    return new HttpEntity<>(headers);
  }

  /**
   * Builds the API URL with the date range appended.
   *
   * @param now The current date and time
   * @param oldestUpdatedDate The oldest updated date
   * @param newestUpdatedDate The newest updated date
   * @param pageNumber The page number
   * @return The API URL with the date range appended
   */
  public String buildApiUrl(
    LocalDateTime now,
    LocalDateTime oldestUpdatedDate,
    LocalDateTime newestUpdatedDate,
    int pageNumber
  ) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
      "yyyy-MM-dd'T'HH:mm:ss"
    );

    String category = "IT";
    String size = String.valueOf(batchSize);
    String published =
      "(" +
      now.minusMonths(6).format(formatter) +
      "," +
      now.format(formatter) +
      ")";
    String updated =
      "(" +
      oldestUpdatedDate.format(formatter) +
      "," +
      newestUpdatedDate.format(formatter) +
      ")";

    String pageNumberArg = pageNumber == -1 ? "" : "&page=" + pageNumber;

    return (
      apiUrl +
      "?category=" +
      category +
      "&size=" +
      size +
      "&published=" +
      published +
      "&updated=" +
      updated +
      pageNumberArg
    );
  }
}
