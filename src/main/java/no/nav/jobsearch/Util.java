package no.nav.jobsearch;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

public final class Util {

  private Util() {
    // Prevent instantiation
  }

  /**
   * Parses a date-time string to a {@link LocalDateTime} object.
   *
   * @param dateTimeString The date-time string to parse
   * @return The parsed {@link LocalDateTime} object, or {@code null} if the input string is {@code null}
   */
  public static LocalDateTime parseToLocalDateTime(String dateTimeString) {
    return Optional
      .ofNullable(dateTimeString)
      .map(dateTime -> ZonedDateTime
        .parse(dateTime)
        .withZoneSameInstant(ZoneId.of("UTC"))
        .toLocalDateTime()
      )
      .orElse(null);
  }
}
