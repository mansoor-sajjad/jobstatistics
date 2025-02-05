package no.nav.jobsearch.service;

import no.nav.jobsearch.model.JobAd;
import no.nav.jobsearch.model.JobAdDTO;
import no.nav.jobsearch.model.JobFeedResponse;
import no.nav.jobsearch.repository.JobAdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FeedService {

    private static final Logger logger = LoggerFactory.getLogger(FeedService.class);

    private static final int BATCH_SIZE = 100;

    @Value("${api.token}")
    private String token;

    @Value("${feed.api.url}?category=IT&size=" + BATCH_SIZE)
    private String apiUrlForIT;

    private final RestTemplate restTemplate;
    private final JobAdRepository jobAdRepository;

    public FeedService(RestTemplate restTemplate, JobAdRepository jobAdRepository) {
        this.restTemplate = restTemplate;
        this.jobAdRepository = jobAdRepository;
    }

    private HttpEntity<String> getHttpEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/json");
        return new HttpEntity<>(headers);
    }

    @Transactional
    public void fetchAndSaveAllITJobs() {
        LocalDateTime oldestPublishedDate = LocalDateTime.now().minusMonths(6);
        LocalDateTime newestPublishedDate = LocalDateTime.now();

        while(true) {
            String url = buildApiUrl(oldestPublishedDate, newestPublishedDate);

            ResponseEntity<JobFeedResponse> response = fetchDataWithRetry(url);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.error("Failed to fetch data from API or received invalid response for URL: {}", url);
                break;
            }

            JobFeedResponse jobFeedResponse = response.getBody();
            newestPublishedDate = handleResponse(jobFeedResponse);

            if (newestPublishedDate == null) {
                logger.info("No more jobs found or invalid response. Stopping fetch process.");
                break;
            }

            int totalPages = jobFeedResponse.getTotalPages();
            for (int pageNumber = jobFeedResponse.getPageNumber() + 1; pageNumber < totalPages; pageNumber++) {
                String pageUrl = url + "&page=" + pageNumber;
                ResponseEntity<JobFeedResponse> pageResponse = fetchDataWithRetry(pageUrl);

                if (!pageResponse.getStatusCode().is2xxSuccessful() || pageResponse.getBody() == null) {
                    logger.warn("Failed to fetch data for page {} or received invalid response. Skipping page.", pageNumber);
                    continue;
                }

                JobFeedResponse pageJobFeedResponse = pageResponse.getBody();
                newestPublishedDate = handleResponse(pageJobFeedResponse);

                if (newestPublishedDate == null) {
                    logger.info("No more jobs found on page {}. Stopping fetch process.", pageNumber);
                    break;
                }
            }

            if (newestPublishedDate == null || !newestPublishedDate.isAfter(oldestPublishedDate)) {
                logger.info("No more jobs found within the date range. Stopping fetch process.");
                break;
            }
        }
    }

    private String buildApiUrl(LocalDateTime oldestPublishedDate, LocalDateTime newestPublishedDate) {
        return apiUrlForIT + "&published=(" + oldestPublishedDate + "," + newestPublishedDate + ")&updated=(" + oldestPublishedDate + "," + newestPublishedDate + ")";
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpClientErrorException.class},
            maxAttemptsExpression = "${retry.maxAttempts:3}",
            backoff = @Backoff(
                    delayExpression = "${retry.maxDelay:5000}",
                    multiplierExpression = "${retry.backoff.multiplier:3}"))
    private ResponseEntity<JobFeedResponse> fetchDataWithRetry(String url) {
        try {
            logger.info("Fetching data from URL: {}", url);
            return restTemplate.exchange(url, HttpMethod.GET, getHttpEntity(), JobFeedResponse.class);
        } catch (ResourceAccessException e) {
            logger.error("Timeout occurred while fetching data from URL: {}", url, e);
            throw e;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error occurred while fetching data from URL: {}", url, e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error occurred while fetching data from URL: {}", url, e);
            throw new RuntimeException("Failed to fetch data after retries", e);
        }
    }

    private LocalDateTime handleResponse(JobFeedResponse jobFeedResponse) {
        if (jobFeedResponse != null && jobFeedResponse.getContent() != null && !jobFeedResponse.getContent().isEmpty()) {
            processAndStoreJobsInBatches(jobFeedResponse.getContent());
            return jobFeedResponse.getContent().getLast().getPublishedAsLocalDateTime();
        }
        return null;
    }

    private void processAndStoreJobsInBatches(List<JobAdDTO> jobAds) {
        logger.info("Processing and storing {} jobs in batches.", jobAds.size());

        List<JobAd> batch = new ArrayList<>();

        for (JobAdDTO ad : jobAds) {
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

    private String getNewestUpdatedDate() {
        return jobAdRepository.findNewestUpdatedDate()
                .map(LocalDateTime::toString)
                .orElse(LocalDateTime.now().toString());
    }

    private void removeExpiredAndUnpublishedAds() {
        List<String> activeAdUuids = getActiveAdUuidsFromFeed();
        Iterable<JobAd> storedAds = jobAdRepository.findAll();

        for (JobAd ad : storedAds) {
            if (ad.getExpires().isBefore(LocalDateTime.now()) || !activeAdUuids.contains(ad.getUuid())) {
                jobAdRepository.delete(ad);
            }
        }
    }

    private List<String> getActiveAdUuidsFromFeed() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List<JobAdDTO>> response = restTemplate.exchange(
                apiUrlForIT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

        List<JobAdDTO> jobAds = response.getBody();
        return jobAds.stream().map(JobAdDTO::getUuid).toList();
    }
}