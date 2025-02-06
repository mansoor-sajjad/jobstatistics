package no.nav.jobsearch.model;

import static no.nav.jobsearch.Util.parseToLocalDateTime;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.Getter;

/**
 * Represents a job ad.
 * Instances of this class are persisted in the database.
 */
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

  public JobAd(JobAdDto dto) {
    this.uuid = dto.getUuid();
    this.title = dto.getTitle();
    this.description = dto.getDescription();
    this.published = parseToLocalDateTime(dto.getPublished());
    this.updated = parseToLocalDateTime(dto.getUpdated());
    this.expires = parseToLocalDateTime(dto.getExpires());
  }

  public void updateFromDto(JobAdDto dto) {
    this.title = dto.getTitle();
    this.description = dto.getDescription();
    this.published = parseToLocalDateTime(dto.getPublished());
    this.updated = parseToLocalDateTime(dto.getUpdated());
    this.expires = parseToLocalDateTime(dto.getExpires());
  }
}
