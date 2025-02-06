package no.nav.jobsearch.model;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobStatisticsTest {

  @Test
  void testValidRecordIsConvertedToJobStatistics() {
    LocalDateTime now = LocalDateTime.of(2024, 2, 1, 0, 0);
    Object[] record = { now, 5L, 10L, 20L };

    JobStatistics stats = JobStatistics.of(record);

    assertThat(stats).isNotNull();
    assertThat(stats.weekStart()).isEqualTo(now);
    assertThat(stats.kotlinCount()).isEqualTo(5L);
    assertThat(stats.javaCount()).isEqualTo(10L);
    assertThat(stats.totalCount()).isEqualTo(20L);
  }

  @Test
  void testInvalidRecordReturnsNull() {
    Object[] invalidRecord1 = { null, 5L, 10L, 20L };
    Object[] invalidRecord2 = { LocalDateTime.now(), "invalid", 10L, 20L };
    Object[] invalidRecord3 = { LocalDateTime.now(), 5L, 10L };
    Object[] nullRecord = null;

    assertThat(JobStatistics.of(invalidRecord1)).isNull();
    assertThat(JobStatistics.of(invalidRecord2)).isNull();
    assertThat(JobStatistics.of(invalidRecord3)).isNull();
    assertThat(JobStatistics.of(nullRecord)).isNull();
  }

  @Test
  void testAggregationOfJobStatisticsByWeek() {
    LocalDateTime week1 = LocalDateTime.of(2024, 1, 1, 0, 0);
    LocalDateTime week2 = LocalDateTime.of(2024, 1, 8, 0, 0);

    List<Object[]> records = List.of(
      new Object[] { week1, 5L, 10L, 20L },
      new Object[] { week1, 3L, 6L, 12L },
      new Object[] { week2, 2L, 4L, 8L }
    );

    List<JobStatistics> result = JobStatistics.of(records);

    assertThat(result).hasSize(2);

    // Week 1 stats
    assertThat(result.getFirst().weekStart()).isEqualTo(week1);
    assertThat(result.getFirst().kotlinCount()).isEqualTo(8L);
    assertThat(result.getFirst().javaCount()).isEqualTo(16L);
    assertThat(result.getFirst().totalCount()).isEqualTo(32L);

    // Week 2 stats
    assertThat(result.get(1).weekStart()).isEqualTo(week2);
    assertThat(result.get(1).kotlinCount()).isEqualTo(2L);
    assertThat(result.get(1).javaCount()).isEqualTo(4L);
    assertThat(result.get(1).totalCount()).isEqualTo(8L);
  }

  @Test
  void testSortingOfAggregatedStatistics() {
    LocalDateTime week3 = LocalDateTime.of(2024, 1, 15, 0, 0);
    LocalDateTime week1 = LocalDateTime.of(2024, 1, 1, 0, 0);
    LocalDateTime week2 = LocalDateTime.of(2024, 1, 8, 0, 0);

    List<Object[]> records = List.of(
      new Object[] { week3, 4L, 8L, 16L },
      new Object[] { week1, 2L, 5L, 10L },
      new Object[] { week2, 3L, 7L, 14L }
    );

    List<JobStatistics> result = JobStatistics.of(records);

    assertThat(result).hasSize(3);
    assertThat(result)
      .extracting(JobStatistics::weekStart)
      .containsExactly(week1, week2, week3);
  }

  @Test
  void testWeekStartIsNormalized() {
    LocalDateTime randomWednesday = LocalDateTime.of(2024, 2, 7, 14, 30); // Wednesday
    JobStatistics stats = JobStatistics.of(
      new Object[] { randomWednesday, 5L, 10L, 20L }
    );

    LocalDateTime expectedMonday = LocalDateTime.of(2024, 2, 5, 0, 0); // Monday at 00:00
    assertThat(stats.weekStart()).isEqualTo(expectedMonday);
  }
}
