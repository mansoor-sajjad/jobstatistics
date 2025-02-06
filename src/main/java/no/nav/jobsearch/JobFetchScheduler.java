package no.nav.jobsearch;

import java.time.LocalDateTime;
import no.nav.jobsearch.service.FeedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobFetchScheduler {

  @Value("${api.token}")
  private String token;

  private final FeedService feedService;

  public JobFetchScheduler(FeedService feedService) {
    this.feedService = feedService;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(cron = "${update.all.jobs.cron.expression:0 0 0 * * *}")
  public void updateAllJobs() {
    feedService.fetchAndUpdateAllITJobs(LocalDateTime.now());
  }

  @Scheduled(cron = "${updated.jobs.cron.expression:0 */10 * * * *}")
  public void fetchUpdatedJobs() {
    feedService.fetchAndSaveUpdatedJobs(LocalDateTime.now());
  }
}
