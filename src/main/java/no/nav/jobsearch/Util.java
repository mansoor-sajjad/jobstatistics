package no.nav.jobsearch;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

public final class Util {

  private Util() {
    // Prevent instantiation
  }

  public static LocalDateTime parseToLocalDateTime(String dateTimeString) {
    return Optional
      .ofNullable(dateTimeString)
      .map(ZonedDateTime::parse)
      .map(zonedDateTime ->
        zonedDateTime
          .withZoneSameInstant(ZoneId.systemDefault())
          .toLocalDateTime()
      )
      .orElse(null);
  }
}
