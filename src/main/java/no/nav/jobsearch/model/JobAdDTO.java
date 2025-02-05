package no.nav.jobsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobAdDTO {
    private String uuid;
    private String title;
    private String description;
    private String published;
    private String updated;
    private String expires;

    public LocalDateTime getPublishedAsLocalDateTime() {
        return parseToLocalDateTime(published);
    }

    private LocalDateTime parseToLocalDateTime(String dateTimeString) {
        // Parse as Instant (UTC time)
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateTimeString);
        // Convert to LocalDateTime in the system's default time zone
        return zonedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}