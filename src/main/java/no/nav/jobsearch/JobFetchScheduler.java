package no.nav.jobsearch;

import no.nav.jobsearch.service.FeedService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobFetchScheduler {
    private final FeedService feedService;

    public JobFetchScheduler(FeedService feedService) {
        this.feedService = feedService;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void fetchDaily() {
        feedService.fetchAndSaveJobs();
    }
}