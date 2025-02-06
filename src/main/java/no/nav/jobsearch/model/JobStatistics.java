package no.nav.jobsearch.model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

public record JobStatistics(
  LocalDateTime weekStart,
  int weekNumber,
  long kotlinCount,
  long javaCount,
  long totalCount
) {
  public static JobStatistics of(Object[] record) {
    return Optional
      .ofNullable(record)
      .filter(JobStatistics::isValid)
      .map(JobStatistics::ofValid)
      .orElse(null);
  }

  private static JobStatistics ofValid(Object[] record) {
    LocalDateTime dateTime = (LocalDateTime) record[0];
    LocalDateTime weekStart = getWeekStart(dateTime); // Normalize to week start

    return new JobStatistics(
      weekStart,
      weekStart.get(WeekFields.ISO.weekOfWeekBasedYear()),
      (long) record[1],
      (long) record[2],
      (long) record[3]
    );
  }

  private static boolean isValid(Object[] record) {
    return (
      record != null &&
      record.length == 4 &&
      record[0] instanceof LocalDateTime &&
      record[1] instanceof Long &&
      record[2] instanceof Long &&
      record[3] instanceof Long
    );
  }

  public static List<JobStatistics> of(List<Object[]> statistics) {
    return statistics
      .stream()
      .map(JobStatistics::of)
      .filter(Objects::nonNull)
      .collect(Collectors.groupingBy(stat -> getWeekKey(stat.weekStart())))
      .values()
      .stream()
      .map(JobStatistics::aggregate)
      .sorted(Comparator.comparing(JobStatistics::weekStart))
      .toList();
  }

  private static JobStatistics aggregate(List<JobStatistics> weeklyStats) {
    LocalDateTime weekStart = weeklyStats.getFirst().weekStart(); // Use normalized start-of-week

    return new JobStatistics(
      weekStart,
      weekStart.get(WeekFields.ISO.weekOfWeekBasedYear()),
      weeklyStats.stream().mapToLong(JobStatistics::kotlinCount).sum(),
      weeklyStats.stream().mapToLong(JobStatistics::javaCount).sum(),
      weeklyStats.stream().mapToLong(JobStatistics::totalCount).sum()
    );
  }

  private static String getWeekKey(LocalDateTime dateTime) {
    LocalDateTime weekStart = getWeekStart(dateTime);
    int weekOfYear = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear());
    return weekStart.getYear() + "-W" + weekOfYear;
  }

  private static LocalDateTime getWeekStart(LocalDateTime dateTime) {
    return dateTime
      .with(WeekFields.ISO.dayOfWeek(), 1) // Set to Monday
      .with(LocalTime.MIDNIGHT); // Set to 00:00:00
  }
}
