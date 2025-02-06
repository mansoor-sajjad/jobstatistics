package no.nav.jobsearch.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
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

  @Value("${feed.api.url}?category=IT&size=" + BATCH_SIZE)
  private String apiUrlForIT;

  private final RestTemplate restTemplate;

  public JobFetcher(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public void fetchJobs(
    LocalDateTime now,
    LocalDateTime oldestUpdatedDate,
    LocalDateTime newestUpdatedDate,
    Consumer<List<JobAdDto>> dataBatchHandler
  ) {
    while (true) {
      String url = buildApiUrl(now, oldestUpdatedDate, newestUpdatedDate);

      ResponseEntity<JobFeedResponse> response = fetchDataWithRetry(url);

      if (
        !response.getStatusCode().is2xxSuccessful() ||
        response.getBody() == null
      ) {
        logger.error(
          "Failed to fetch data from API or received invalid response for URL: {}",
          url
        );
        break;
      }

      JobFeedResponse jobFeedResponse = response.getBody();
      newestUpdatedDate = handleResponse(jobFeedResponse, dataBatchHandler);

      if (newestUpdatedDate == null) {
        logger.info(
          "No more jobs found or invalid response. Stopping fetch process."
        );
        break;
      }

      int totalPages = jobFeedResponse.getTotalPages();
      for (
        int pageNumber = jobFeedResponse.getPageNumber() + 1;
        pageNumber < totalPages;
        pageNumber++
      ) {
        String pageUrl = url + "&page=" + pageNumber;
        ResponseEntity<JobFeedResponse> pageResponse = fetchDataWithRetry(
          pageUrl
        );

        if (
          !pageResponse.getStatusCode().is2xxSuccessful() ||
          pageResponse.getBody() == null
        ) {
          logger.warn(
            "Failed to fetch data for page {} or received invalid response. Skipping page.",
            pageNumber
          );
          continue;
        }

        JobFeedResponse pageJobFeedResponse = pageResponse.getBody();
        newestUpdatedDate =
          handleResponse(pageJobFeedResponse, dataBatchHandler);

        if (newestUpdatedDate == null) {
          logger.info(
            "No more jobs found on page {}. Stopping fetch process.",
            pageNumber
          );
          break;
        }
      }

      if (
        newestUpdatedDate == null ||
        !newestUpdatedDate.isAfter(oldestUpdatedDate)
      ) {
        logger.info(
          "No more jobs found within the date range. Stopping fetch process."
        );
        break;
      }
    }
  }

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

  private String buildApiUrl(
    LocalDateTime now,
    LocalDateTime oldestUpdatedDate,
    LocalDateTime newestUpdatedDate
  ) {
    return (
      apiUrlForIT +
      "&published=(" +
      now.minusMonths(6) +
      "," +
      now +
      ")&updated=(" +
      oldestUpdatedDate +
      "," +
      newestUpdatedDate +
      ")"
    );
  }

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

  private HttpEntity<String> getHttpEntity() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + token);
    headers.set("Accept", "application/json");
    return new HttpEntity<>(headers);
  }
}
