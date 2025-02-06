package no.nav.jobsearch.service;

import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import no.nav.jobsearch.model.JobAd;
import no.nav.jobsearch.model.JobAdDto;
import no.nav.jobsearch.model.JobFeedResponse;
import no.nav.jobsearch.repository.JobAdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class FeedServiceTest {

  @Mock
  private JobAdRepository jobAdRepository;

  @Mock
  private JobFetcher jobFetcher;

  @InjectMocks
  private FeedService feedService;

  private JobAdDto jobAdDto1;
  private JobAdDto jobAdDto2;
  private JobAd jobAd1;
  private JobAd jobAd2;

  @BeforeEach
  public void setUp() {
    jobAdDto1 = new JobAdDto();
    jobAdDto1.setUuid("uuid1");
    jobAdDto1.setTitle("Title 1");
    jobAdDto1.setDescription("Description 1");
    jobAdDto1.setPublished("2025-01-01T00:00:00Z");
    jobAdDto1.setUpdated("2025-01-01T00:00:00Z");
    jobAdDto1.setExpires("2025-03-01T00:00:00Z");

    jobAdDto2 = new JobAdDto();
    jobAdDto2.setUuid("uuid2");
    jobAdDto2.setTitle("Title 2");
    jobAdDto2.setDescription("Description 2");
    jobAdDto2.setPublished("2025-01-02T00:00:00Z");
    jobAdDto2.setUpdated("2025-01-02T00:00:00Z");
    jobAdDto2.setExpires("2025-03-03T00:00:00Z");

    jobAd1 = new JobAd(jobAdDto1);
    jobAd2 = new JobAd(jobAdDto2);

    ReflectionTestUtils.setField(feedService, "batchSize", 100);
  }

  @Test
  public void testFetchAndUpdateAllITJobs() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime sixMonthsAgo = now.minusMonths(6);

    JobFeedResponse response = new JobFeedResponse(
      List.of(jobAdDto1, jobAdDto2),
      0,
      1
    );

    // Mock the fetchJobs method to invoke the Consumer with the response content
    // The response contains jobAd1 and jobAd2 that should be saved to the database.
    doAnswer(invocation -> {
        Consumer<List<JobAdDto>> consumer = invocation.getArgument(3);
        consumer.accept(response.getContent());
        return null;
      })
      .when(jobFetcher)
      .fetchJobs(eq(now), eq(sixMonthsAgo), eq(now), any(Consumer.class));

    feedService.fetchAndUpdateAllITJobs(now);

    verify(jobFetcher, times(1))
      .fetchJobs(eq(now), eq(sixMonthsAgo), eq(now), any(Consumer.class));
    verify(jobAdRepository, times(1)).saveAll(List.of(jobAd1, jobAd2));
    verify(jobAdRepository, times(1)).findAll();
  }

  @Test
  public void testFetchAndUpdateAllITJobsFiltersExpiredAds() {
    LocalDateTime now = LocalDateTime.parse("2025-03-02T00:00:00");
    LocalDateTime sixMonthsAgo = now.minusMonths(6);

    JobFeedResponse response = new JobFeedResponse(
      List.of(jobAdDto1, jobAdDto2),
      0,
      1
    );

    // Mock the fetchJobs method to invoke the Consumer with the response content
    // The response contains jobAd1 and jobAd2 that should be saved to the database.
    doAnswer(invocation -> {
        Consumer<List<JobAdDto>> consumer = invocation.getArgument(3);
        consumer.accept(response.getContent());
        return null;
      })
      .when(jobFetcher)
      .fetchJobs(eq(now), eq(sixMonthsAgo), eq(now), any(Consumer.class));

    // both jobAd1 and jobAd2 should be returned from the database
    // jobAd1 has expired and should be deleted from the database
    when(jobAdRepository.findAll()).thenReturn(List.of(jobAd1, jobAd2));

    feedService.fetchAndUpdateAllITJobs(now);

    // fetchAndUpdateAllITJobs should fetch jobs from the jobFetcher, with the correct dates
    verify(jobFetcher, times(1))
      .fetchJobs(eq(now), eq(sixMonthsAgo), eq(now), any(Consumer.class));
    // jobAd1 and jobAd2 should be saved to the database
    verify(jobAdRepository, times(1)).saveAll(List.of(jobAd1, jobAd2));
    // jobAd1 should be deleted from the database, as it has expired
    verify(jobAdRepository, times(1)).delete(jobAd1);
    verify(jobAdRepository, times(1)).findAll();
  }

  @Test
  public void testFetchAndSaveUpdatedJobs() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime newestUpdatedDate = now.minusDays(1);

    JobFeedResponse response = new JobFeedResponse(List.of(jobAdDto1), 0, 1);

    when(jobAdRepository.findNewestUpdatedDate())
      .thenReturn(Optional.of(newestUpdatedDate));

    // Mock the fetchJobs method to invoke the Consumer with the response content
    // The response contains jobAd1 that should be saved to the database.
    doAnswer(invocation -> {
        Consumer<List<JobAdDto>> consumer = invocation.getArgument(3);
        consumer.accept(response.getContent());
        return null;
      })
      .when(jobFetcher)
      .fetchJobs(eq(now), eq(newestUpdatedDate), eq(now), any(Consumer.class));

    feedService.fetchAndSaveUpdatedJobs(now);

    verify(jobAdRepository, times(1)).findNewestUpdatedDate();
    verify(jobFetcher, times(1))
      .fetchJobs(eq(now), eq(newestUpdatedDate), eq(now), any(Consumer.class));
    verify(jobAdRepository, times(1)).saveAll(List.of(jobAd1));
  }

  @Test
  public void testProcessAndStoreJobsInBatches() {
    List<JobAdDto> jobAdDtos = List.of(jobAdDto1, jobAdDto2);

    // Some of the jobAds already exist in the database, and some do not.
    // All jobAds should be saved to the database.
    when(jobAdRepository.findByUuid("uuid1")).thenReturn(Optional.of(jobAd1));
    when(jobAdRepository.findByUuid("uuid2")).thenReturn(Optional.empty());

    feedService.processAndStoreJobsInBatches(jobAdDtos);

    verify(jobAdRepository, times(1)).findByUuid("uuid1");
    verify(jobAdRepository, times(1)).findByUuid("uuid2");
    verify(jobAdRepository, times(1)).saveAll(List.of(jobAd1, jobAd2));
  }

  @Test
  public void testNonExpiredAndPublishedAdsShouldNotBeRemoved() {
    List<String> activeAdUuids = List.of("uuid1", "uuid2");

    when(jobAdRepository.findAll()).thenReturn(List.of(jobAd1, jobAd2));

    // Setting the now() time to be before the expiration date of both jobAd1 and jobAd2
    // So to mock that no jobAd has expired.
    LocalDateTime now = LocalDateTime.parse("2025-02-01T00:00:00");

    // both jobAd1 and jobAd2 should not be deleted, as they are in the activeAdUuids list returned by the jodFetcher.
    feedService.removeExpiredAndUnpublishedAds(now, activeAdUuids);

    verify(jobAdRepository, times(1)).findAll();
    verify(jobAdRepository, never()).delete(any(JobAd.class));
  }

  @Test
  public void testRemoveUnpublishedAds() {
    List<String> activeAdUuids = List.of("uuid1");

    when(jobAdRepository.findAll()).thenReturn(List.of(jobAd1, jobAd2));

    // Setting the now() time to be before the expiration date of both jobAd1 and jobAd2
    // So to mock that no jobAd has expired.
    LocalDateTime now = LocalDateTime.parse("2025-02-01T00:00:00");

    // jobAd2 should be deleted, as it is not in the activeAdUuids list returned by the jodFetcher.
    feedService.removeExpiredAndUnpublishedAds(now, activeAdUuids);

    verify(jobAdRepository, times(1)).findAll();
    verify(jobAdRepository, times(1)).delete(jobAd2);
  }

  @Test
  public void testRemoveExpiredAds() {
    List<String> activeAdUuids = List.of("uuid2");

    when(jobAdRepository.findAll()).thenReturn(List.of(jobAd1, jobAd2));

    // Setting the now() time to be one day after the expiration date of jobAd1
    // So to mock that the jobAd1 has expired and should be deleted from the database.
    // jobAd2 should not be deleted as it has not expired.
    LocalDateTime now = jobAd1.getExpires().plusDays(1);
    feedService.removeExpiredAndUnpublishedAds(now, activeAdUuids);

    verify(jobAdRepository, times(1)).findAll();
    verify(jobAdRepository, times(1)).delete(jobAd1);
    verify(jobAdRepository, never()).delete(jobAd2);
  }
}
