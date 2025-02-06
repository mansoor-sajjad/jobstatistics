package no.nav.jobsearch.model;

import static no.nav.jobsearch.Util.parseToLocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobAdDto {

  private String uuid;
  private String title;
  private String description;
  private String published;
  private String updated;
  private String expires;

  public LocalDateTime getPublishedAsLocalDateTime() {
    return parseToLocalDateTime(published);
  }
}
