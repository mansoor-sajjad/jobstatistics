package no.nav.jobsearch;

import java.time.LocalDateTime;
import no.nav.jobsearch.service.FeedService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class JobFetchScheduler {

  private final FeedService feedService;

  public JobFetchScheduler(FeedService feedService) {
    this.feedService = feedService;
  }

  /**
   * Updates all jobs.
   * Configured to run once on application startup and then every day at midnight by default.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(cron = "${update.all.jobs.cron.expression:0 0 0 * * *}")
  public void updateAllJobs() {
    feedService.fetchAndUpdateAllITJobs(LocalDateTime.now());
  }

  /**
   * Fetches updated jobs.
   * Configured to run every 10 minutes by default.
   */
  @Scheduled(cron = "${updated.jobs.cron.expression:0 */10 * * * *}")
  public void fetchUpdatedJobs() {
    feedService.fetchAndSaveUpdatedJobs(LocalDateTime.now());
  }
}
