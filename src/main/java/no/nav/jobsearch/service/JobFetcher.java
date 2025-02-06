package no.nav.jobsearch.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import no.nav.jobsearch.model.JobAdDto;
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
public class JobFetcher {

  private static final Logger logger = LoggerFactory.getLogger(
    JobFetcher.class
  );

  public static final int BATCH_SIZE = 100;

  @Value("${api.token}")
  private String token;

  @Value("${feed.api.url}")
  private String apiUrl;

  private final RestTemplate restTemplate;

  public JobFetcher(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  /**
   * Fetches jobs from the API and processes them in batches.
   * The method fetches jobs from the API until no more jobs are found
   * or the newest updated date is before the oldest updated date.
   *
   * @param now The current date and time
   * @param oldestUpdatedDate The oldest updated date to fetch jobs from
   * @param newestUpdatedDate The newest updated date to fetch jobs from
   * @param dataBatchHandler The handler for processing the fetched data batch
   */
  public void fetchJobs(
    LocalDateTime now,
    LocalDateTime oldestUpdatedDate,
    LocalDateTime newestUpdatedDate,
    Consumer<List<JobAdDto>> dataBatchHandler
  ) {
    while (newestUpdatedDate == null || newestUpdatedDate.isAfter(oldestUpdatedDate)) {
      String url = buildApiUrl(now, oldestUpdatedDate, newestUpdatedDate);
      ResponseEntity<JobFeedResponse> response = fetchDataWithRetry(url);

      if (!isValidResponse(response)) {
        logger.error("Failed to fetch data from API or received invalid response for URL: {}", url);
        break;
      }

      JobFeedResponse jobFeedResponse = response.getBody();
      newestUpdatedDate = handleResponse(jobFeedResponse, dataBatchHandler);

      if (newestUpdatedDate == null) {
        logger.info("No more jobs found or invalid response. Stopping fetch process.");
        break;
      }

      int totalPages = jobFeedResponse.getTotalPages();
      int currentPage = jobFeedResponse.getPageNumber();

      newestUpdatedDate = IntStream.range(currentPage + 1, totalPages)
              .mapToObj(pageNumber -> {
                return fetchDataWithRetry(url + "&page=" + pageNumber);
              })
              .filter(this::isValidResponse)
              .map(ResponseEntity::getBody)
              .map(pageJobFeedResponse -> handleResponse(pageJobFeedResponse, dataBatchHandler))
              .takeWhile(Objects::nonNull)
              .reduce((a, b) -> b) // Get the last non-null newestUpdatedDate
              .orElse(null);

      if (newestUpdatedDate == null || !newestUpdatedDate.atZone(ZoneOffset.UTC).isAfter(oldestUpdatedDate.atZone(ZoneOffset.UTC))) {
        logger.info("No more jobs found within the date range. Stopping fetch process.");
        break;
      }
    }
  }

  private boolean isValidResponse(ResponseEntity<JobFeedResponse> response) {
    return response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
  }

  /**
   * Validates the response and processes the data.
   * If the response is valid and contains data, the data is processed by the data handler.
   * The method returns the newest updated date from the response, which is the last updated date in the data.
   * If the response is invalid or does not contain data, the method returns null.
   *
   * @param jobFeedResponse The response from the API
   * @param dataHandler The handler for processing the fetched data
   * @return The newest updated date from the response, or null if the response is invalid or does not contain data
   */
  private LocalDateTime handleResponse(
    JobFeedResponse jobFeedResponse,
    Consumer<List<JobAdDto>> dataHandler
  ) {
    if (
      jobFeedResponse != null &&
      jobFeedResponse.getContent() != null &&
      !jobFeedResponse.getContent().isEmpty()
    ) {
      dataHandler.accept(jobFeedResponse.getContent());
      return jobFeedResponse
        .getContent()
        .getLast()
        .getPublishedAsLocalDateTime();
    }
    return null;
  }

  /**
   * Builds the API URL with the date range appended.
   *
   * @param now The current date and time
   * @param oldestUpdatedDate The oldest updated date
   * @param newestUpdatedDate The newest updated date
   * @return The API URL with the date range appended
   */
  private String buildApiUrl(
    LocalDateTime now,
    LocalDateTime oldestUpdatedDate,
    LocalDateTime newestUpdatedDate
  ) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    String category = "IT";
    String size = String.valueOf(BATCH_SIZE);
    String published = "(" + now.minusMonths(6).format(formatter) + "," + now.format(formatter) + ")";
    String updated = "(" + oldestUpdatedDate.format(formatter) + "," + newestUpdatedDate.format(formatter) + ")";

    return apiUrl + "?category=" + category + "&size=" + size + "&published=" + published + "&updated=" + updated;
  }

  /**
   * Fetches data from the API with retry logic.
   * The method retries fetching data from the API if a ResourceAccessException or HttpClientErrorException occurs.
   * The method retries fetching data up to the maximum number of attempts defined by the configuration.
   * The method uses exponential backoff with a multiplier to delay retries.
   *
   * @param url The URL to fetch data from
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
  private ResponseEntity<JobFeedResponse> fetchDataWithRetry(String url) {
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
  private HttpEntity<String> getHttpEntity() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + token);
    headers.set("Accept", "application/json");
    return new HttpEntity<>(headers);
  }
}
