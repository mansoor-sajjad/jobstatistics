package no.nav.jobsearch.service;

import static no.nav.jobsearch.service.JobFetcher.BATCH_SIZE;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import no.nav.jobsearch.model.JobAd;
import no.nav.jobsearch.model.JobAdDto;
import no.nav.jobsearch.repository.JobAdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService {

  private static final Logger logger = LoggerFactory.getLogger(
    FeedService.class
  );

  private final JobAdRepository jobAdRepository;

  private final JobFetcher jobFetcher;

  public FeedService(JobAdRepository jobAdRepository, JobFetcher jobFetcher) {
    this.jobAdRepository = jobAdRepository;
    this.jobFetcher = jobFetcher;
  }

  @Transactional
  public void fetchAndUpdateAllITJobs(LocalDateTime now) {
    LocalDateTime oldestPublishedDate = now.minusMonths(6);
    List<String> activeAdUuids = new ArrayList<>();
    jobFetcher.fetchJobs(
      now,
      oldestPublishedDate,
      now,
      jobAdDtos -> {
        processAndStoreJobsInBatches(jobAdDtos);
        jobAdDtos.forEach(jobAdDto -> activeAdUuids.add(jobAdDto.getUuid()));
      }
    );
    removeExpiredAndUnpublishedAds(now, activeAdUuids);
  }

  @Transactional
  public void fetchAndSaveUpdatedJobs(LocalDateTime now) {
    jobAdRepository
      .findNewestUpdatedDate()
      .ifPresent(newestUpdatedDate ->
        jobFetcher.fetchJobs(
          now,
          newestUpdatedDate,
          now,
          this::processAndStoreJobsInBatches
        )
      );
  }

  void processAndStoreJobsInBatches(List<JobAdDto> jobAds) {
    logger.info("Processing and storing {} jobs in batches.", jobAds.size());

    List<JobAd> batch = new ArrayList<>();

    for (JobAdDto ad : jobAds) {
      Optional<JobAd> existingAd = jobAdRepository.findByUuid(ad.getUuid());

      if (existingAd.isPresent()) {
        JobAd jobAd = existingAd.get();
        jobAd.updateFromDto(ad); // Update existing ad
        batch.add(jobAd);
      } else {
        batch.add(new JobAd(ad)); // Create new ad
      }

      // If batch reaches defined size, save and clear it
      if (batch.size() >= BATCH_SIZE) {
        jobAdRepository.saveAll(batch);
        batch.clear();
      }
    }

    // Save remaining items in batch
    if (!batch.isEmpty()) {
      jobAdRepository.saveAll(batch);
    }
  }

  void removeExpiredAndUnpublishedAds(
    LocalDateTime now,
    List<String> activeAdUuids
  ) {
    Iterable<JobAd> storedAds = jobAdRepository.findAll();

    for (JobAd ad : storedAds) {
      if (
        ad.getExpires().isBefore(now) || !activeAdUuids.contains(ad.getUuid())
      ) {
        jobAdRepository.delete(ad);
      }
    }
  }
}
