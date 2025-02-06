package no.nav.jobsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

/**
 * Represents a response from the external job feed.
 * Instances of this class are used to map JSON data from the external job feed.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobFeedResponse {

  private List<JobAdDto> content;
  private int pageNumber;
  private int totalPages;

  public JobFeedResponse(
    List<JobAdDto> content,
    int pageNumber,
    int totalPages
  ) {
    this.content = content;
    this.pageNumber = pageNumber;
    this.totalPages = totalPages;
  }

  public JobFeedResponse() {}
}
