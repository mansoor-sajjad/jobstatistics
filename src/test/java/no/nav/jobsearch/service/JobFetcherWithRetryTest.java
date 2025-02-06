package no.nav.jobsearch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import no.nav.jobsearch.model.JobFeedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class JobFetcherWithRetryTest {

  @Mock
  private RestTemplate restTemplate;

  @InjectMocks
  private JobFetcherWithRetry jobFetcherWithRetry;

  @BeforeEach
  void setUp() {
    jobFetcherWithRetry = new JobFetcherWithRetry(restTemplate);
  }

  @Test
  void testFetchDataWithRetry_SuccessfulResponse() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime oldestUpdatedDate = now.minusDays(10);
    LocalDateTime newestUpdatedDate = now.minusDays(1);
    int pageNumber = 1;

    ResponseEntity<JobFeedResponse> mockResponse = mock(ResponseEntity.class);
    when(
      restTemplate.exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      )
    )
      .thenReturn(mockResponse);

    ResponseEntity<JobFeedResponse> response =
      jobFetcherWithRetry.fetchDataWithRetry(
        now,
        oldestUpdatedDate,
        newestUpdatedDate,
        pageNumber
      );

    assertNotNull(response);
    verify(restTemplate, times(1))
      .exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      );
  }

  @Test
  void testFetchDataWithRetry_HttpClientErrorException() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime oldestUpdatedDate = now.minusDays(10);
    LocalDateTime newestUpdatedDate = now.minusDays(1);
    int pageNumber = 1;

    when(
      restTemplate.exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      )
    )
      .thenThrow(HttpClientErrorException.class);

    assertThrows(
      HttpClientErrorException.class,
      () ->
        jobFetcherWithRetry.fetchDataWithRetry(
          now,
          oldestUpdatedDate,
          newestUpdatedDate,
          pageNumber
        )
    );

    verify(restTemplate, times(1))
      .exchange(
        anyString(),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(JobFeedResponse.class)
      );
  }

  @Test
  void testBuildApiUrl() {
    LocalDateTime now = LocalDateTime.of(2024, 2, 6, 12, 0, 0);
    LocalDateTime oldestUpdatedDate = now.minusDays(10);
    LocalDateTime newestUpdatedDate = now.minusDays(1);
    int pageNumber = 1;

    String expectedUrlPart = "&page=1";
    String url = jobFetcherWithRetry.buildApiUrl(
      now,
      oldestUpdatedDate,
      newestUpdatedDate,
      pageNumber
    );

    assertNotNull(url);
    assertTrue(url.contains(expectedUrlPart));
  }

  @Test
  void testGetHttpEntity() {
    HttpEntity<String> httpEntity = jobFetcherWithRetry.getHttpEntity();
    HttpHeaders headers = httpEntity.getHeaders();
    assertNotNull(headers);
    assertTrue(headers.containsKey("Authorization"));
    assertTrue(headers.containsKey("Accept"));
  }
}
