package no.nav.jobsearch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import no.nav.jobsearch.model.JobAdDto;
import no.nav.jobsearch.model.JobFeedResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@EnableRetry
public class JobFetcherTest {

  @Autowired
  private JobFetcher jobFetcher;

  @MockitoBean
  private RestTemplate restTemplate;

  @Test
  public void dataBatchHandlerShouldBeCalledWhenDataReceived() {
    LocalDateTime now = LocalDateTime.parse("2025-02-06T15:35:47");
    LocalDateTime oldestUpdatedDate = now.minusDays(30);
    LocalDateTime newestUpdatedDate = now.minusHours(1);

    // Mock the response from the API
    JobFeedResponse jobFeedResponse = new JobFeedResponse();
    jobFeedResponse.setContent(List.of(new JobAdDto()));
    jobFeedResponse.setTotalPages(1);
    jobFeedResponse.setPageNumber(0);

    ResponseEntity<JobFeedResponse> responseEntity = new ResponseEntity<>(
      jobFeedResponse,
      HttpStatus.OK
    );

    // Mock the RestTemplate to return the response
    when(
      restTemplate.exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      )
    )
      .thenReturn(responseEntity);

    // Mock the data batch handler
    Consumer<List<JobAdDto>> dataBatchHandler = mock(Consumer.class);

    // Call the method under test
    jobFetcher.fetchJobs(
      now,
      oldestUpdatedDate,
      newestUpdatedDate,
      dataBatchHandler
    );

    // Verify that the handler was called with the fetched data
    verify(dataBatchHandler, times(1)).accept(anyList());
  }

  @Test
  public void dataBatchHandlerShouldNotBeCalledWhenNoDataReceived() {
    LocalDateTime now = LocalDateTime.parse("2025-02-06T15:35:47");
    LocalDateTime oldestUpdatedDate = now.minusDays(30);
    LocalDateTime newestUpdatedDate = now.minusHours(1);

    // Mock the response from the API with no jobs
    JobFeedResponse jobFeedResponse = new JobFeedResponse();
    jobFeedResponse.setContent(List.of());
    jobFeedResponse.setTotalPages(1);
    jobFeedResponse.setPageNumber(0);

    ResponseEntity<JobFeedResponse> responseEntity = new ResponseEntity<>(
      jobFeedResponse,
      HttpStatus.OK
    );

    // Mock the RestTemplate to return the response
    when(
      restTemplate.exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      )
    )
      .thenReturn(responseEntity);

    // Mock the data batch handler
    Consumer<List<JobAdDto>> dataBatchHandler = mock(Consumer.class);

    // Call the method under test
    jobFetcher.fetchJobs(
      now,
      oldestUpdatedDate,
      newestUpdatedDate,
      dataBatchHandler
    );

    // Verify that the handler was never called (no jobs to process)
    verify(dataBatchHandler, never()).accept(anyList());
  }

  @Test
  public void shouldRetryOnFailure() {
    LocalDateTime now = LocalDateTime.parse("2025-02-06T15:35:47");
    LocalDateTime oldestUpdatedDate = now.minusDays(30);
    LocalDateTime newestUpdatedDate = now.minusDays(10);

    // Mock the RestTemplate to throw exceptions and then return a valid response
    when(
      restTemplate.exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      )
    )
      .thenThrow(ResourceAccessException.class)
      .thenThrow(HttpClientErrorException.class)
      .thenReturn(new ResponseEntity<>(new JobFeedResponse(), HttpStatus.OK));

    // Mock the data batch handler
    Consumer<List<JobAdDto>> dataBatchHandler = mock(Consumer.class);

    // Call the method under test
    jobFetcher.fetchJobs(
      now,
      oldestUpdatedDate,
      newestUpdatedDate,
      dataBatchHandler
    );

    // Verify that the RestTemplate was called 3 times (2 retries + 1 success)
    verify(restTemplate, times(3))
      .exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      );
  }

  @Test
  void shouldEventuallyFailWithExceptionIfItsNotRecovered() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime oldestUpdatedDate = now.minusDays(10);
    LocalDateTime newestUpdatedDate = now.minusDays(1);

    when(
      restTemplate.exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      )
    )
      .thenThrow(new ResourceAccessException("Timeout"))
      .thenThrow(new ResourceAccessException("Timeout"))
      .thenThrow(new ResourceAccessException("Timeout"));

    // Mock the data batch handler
    Consumer<List<JobAdDto>> dataBatchHandler = mock(Consumer.class);

    assertThrows(
      ResourceAccessException.class,
      () ->
        jobFetcher.fetchJobs(
          now,
          oldestUpdatedDate,
          newestUpdatedDate,
          dataBatchHandler
        )
    );

    verify(restTemplate, times(3))
      .exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      );
  }

  @Test
  public void handleResponseShouldReturnValidDate() {
    String publishedTime = "2025-02-06T15:35:47Z";

    // Create a job ad with a published date
    JobAdDto jobAdDto = new JobAdDto();
    jobAdDto.setPublished(publishedTime);

    // Create a response with the job ad
    JobFeedResponse jobFeedResponse = new JobFeedResponse();
    jobFeedResponse.setContent(Collections.singletonList(jobAdDto));

    // Mock the data handler
    Consumer<List<JobAdDto>> dataHandler = mock(Consumer.class);

    // Call the method under test
    LocalDateTime result = jobFetcher.handleResponse(
      jobFeedResponse,
      dataHandler
    );

    // Verify that the handler was called and the correct date was returned
    assertEquals(ZonedDateTime.parse(publishedTime).toLocalDateTime(), result);
    verify(dataHandler, times(1)).accept(anyList());
  }

  @Test
  public void handleResponseShouldReturnValidDateForMultipleJobs() {
    String publishedTime1 = "2025-02-06T15:35:47Z";
    String publishedTime2 = "2025-02-07T15:35:47Z";

    // Create a job ad with a published date
    JobAdDto jobAdDto1 = new JobAdDto();
    jobAdDto1.setPublished(publishedTime1);

    JobAdDto jobAdDto2 = new JobAdDto();
    jobAdDto2.setPublished(publishedTime2);

    // Create a response with the job ad
    JobFeedResponse jobFeedResponse = new JobFeedResponse();
    jobFeedResponse.setContent(List.of(jobAdDto1, jobAdDto2));

    // Mock the data handler
    Consumer<List<JobAdDto>> dataHandler = mock(Consumer.class);

    // Call the method under test
    LocalDateTime result = jobFetcher.handleResponse(
      jobFeedResponse,
      dataHandler
    );

    // Verify that the handler was called and the correct date was returned
    assertEquals(ZonedDateTime.parse(publishedTime2).toLocalDateTime(), result);
    verify(dataHandler, times(1)).accept(anyList());
  }

  @Test
  public void handleResponseShouldReturnNullIfThereAreNoJobs() {
    // Create a response with no jobs
    JobFeedResponse jobFeedResponse = new JobFeedResponse();
    jobFeedResponse.setContent(Collections.emptyList());

    // Mock the data handler
    Consumer<List<JobAdDto>> dataHandler = mock(Consumer.class);

    // Call the method under test
    LocalDateTime result = jobFetcher.handleResponse(
      jobFeedResponse,
      dataHandler
    );

    // Verify that the handler was never called and null was returned
    assertNull(result);
    verify(dataHandler, never()).accept(anyList());
  }
}
