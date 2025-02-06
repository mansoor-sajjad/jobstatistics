package no.nav.jobsearch.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import no.nav.jobsearch.model.JobAdDto;
import no.nav.jobsearch.model.JobFeedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class JobFetcher {

  private static final Logger logger = LoggerFactory.getLogger(
    JobFetcher.class
  );

  private final JobFetcherWithRetry jobFetcherWithRetry;

  public JobFetcher(JobFetcherWithRetry jobFetcherWithRetry) {
    this.jobFetcherWithRetry = jobFetcherWithRetry;
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
    while (
      newestUpdatedDate == null || newestUpdatedDate.isAfter(oldestUpdatedDate)
    ) {
      ResponseEntity<JobFeedResponse> response =
        jobFetcherWithRetry.fetchDataWithRetry(
          now,
          oldestUpdatedDate,
          newestUpdatedDate,
          0
        );

      if (!isValidResponse(response)) {
        logger.error(
          "Failed to fetch data from API or received invalid response"
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
      int currentPage = jobFeedResponse.getPageNumber();

      LocalDateTime finalNewestUpdatedDate = newestUpdatedDate;
      newestUpdatedDate =
        IntStream
          .range(currentPage + 1, totalPages)
          .mapToObj(pageNumber ->
            jobFetcherWithRetry.fetchDataWithRetry(
              now,
              oldestUpdatedDate,
              finalNewestUpdatedDate,
              pageNumber
            )
          )
          .filter(this::isValidResponse)
          .map(ResponseEntity::getBody)
          .map(pageJobFeedResponse ->
            handleResponse(pageJobFeedResponse, dataBatchHandler)
          )
          .takeWhile(Objects::nonNull)
          .reduce((a, b) -> b) // Get the last non-null newestUpdatedDate
          .orElse(null);

      if (
        newestUpdatedDate == null ||
        !newestUpdatedDate
          .atZone(ZoneOffset.UTC)
          .isAfter(oldestUpdatedDate.atZone(ZoneOffset.UTC))
      ) {
        logger.info(
          "No more jobs found within the date range. Stopping fetch process."
        );
        break;
      }
    }
  }

  private boolean isValidResponse(ResponseEntity<JobFeedResponse> response) {
    return (
      response.getStatusCode().is2xxSuccessful() && response.getBody() != null
    );
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
  LocalDateTime handleResponse(
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
}
