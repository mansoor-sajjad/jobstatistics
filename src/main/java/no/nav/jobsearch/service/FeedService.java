package no.nav.jobsearch.service;

import no.nav.jobsearch.model.JobAd;
import no.nav.jobsearch.model.JobFeedResponse;
import no.nav.jobsearch.repository.JobAdRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.util.List;

@Service
public class FeedService {

    @Value("${api.token}")
    private String token;

    private final RestTemplate restTemplate;
    private final JobAdRepository jobAdRepository;
    private static final String FEED_URL = "https://arbeidsplassen.nav.no/public-feed/api/v1/ads";

    public FeedService(RestTemplate restTemplate, JobAdRepository jobAdRepository) {
        this.restTemplate = restTemplate;
        this.jobAdRepository = jobAdRepository;
    }

    public void fetchAndSaveJobs() {

        // Create headers and add the Bearer token
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        // Wrap headers into an HttpEntity
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Send GET request with headers
        ResponseEntity<JobFeedResponse> response = restTemplate.exchange(FEED_URL, HttpMethod.GET, entity, JobFeedResponse.class);
        JobFeedResponse jobFeedResponse = response.getBody();
        if (jobFeedResponse != null && jobFeedResponse.getContent() != null) {
            List<JobAd> ads = jobFeedResponse.getContent().stream().map(job -> {
                JobAd jobAd = new JobAd();
                jobAd.setUuid(job.getUuid());
                jobAd.setTitle(job.getTitle());
                jobAd.setDescription(job.getDescription());
                jobAd.setPublishedDate(LocalDate.parse(job.getPublished().substring(0, 10))); // Henter kun YYYY-MM-DD
                return jobAd;
            }).toList();
            jobAdRepository.saveAll(ads);
        }
    }
}