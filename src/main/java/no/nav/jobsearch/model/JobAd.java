package no.nav.jobsearch.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Entity
@Data
public class JobAd {

    @Getter
    @Id
    private String uuid;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime published;
    private LocalDateTime updated;
    @Getter
    private LocalDateTime expires;

    public JobAd() {}

    public JobAd(JobAdDTO dto) {
        this.uuid = dto.getUuid();
        this.title = dto.getTitle();
        this.description = dto.getDescription();
        this.published = parseToLocalDateTime(dto.getPublished());
        this.updated = parseToLocalDateTime(dto.getUpdated());
        this.expires = parseToLocalDateTime(dto.getExpires());
    }

    public void updateFromDto(JobAdDTO dto) {
        this.title = dto.getTitle();
        this.description = dto.getDescription();
        this.published = parseToLocalDateTime(dto.getPublished());
        this.updated = parseToLocalDateTime(dto.getUpdated());
        this.expires = parseToLocalDateTime(dto.getExpires());
    }

    private LocalDateTime parseToLocalDateTime(String dateTimeString) {
        // Parse as Instant (UTC time)
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateTimeString);
        // Convert to LocalDateTime in the system's default time zone
        return zonedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}