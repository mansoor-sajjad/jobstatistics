package no.nav.jobsearch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.nav.jobsearch.repository.JobAdRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class StatsController {

  private final JobAdRepository jobAdRepository;

  public StatsController(JobAdRepository jobAdRepository) {
    this.jobAdRepository = jobAdRepository;
  }

  @GetMapping("/kotlin-vs-java")
  public List<Map<String, Object>> getKotlinVsJavaStats() {
    LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
    return jobAdRepository
      .getKotlinVsJavaStats(sixMonthsAgo)
      .stream()
      .map(record ->
        Map.of(
          "week",
          record[0],
          "kotlinCount",
          record[1],
          "javaCount",
          record[2]
        )
      )
      .collect(Collectors.toList());
  }
}
