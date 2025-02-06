package no.nav.jobsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobFeedResponse {

  private List<JobAdDto> content;
  private int totalElements;
  private int pageNumber;
  private int pageSize;
  private int totalPages;

  public JobFeedResponse(
    List<JobAdDto> content,
    int totalElements,
    int pageNumber,
    int pageSize,
    int totalPages
  ) {
    this.content = content;
    this.totalElements = totalElements;
    this.pageNumber = pageNumber;
    this.pageSize = pageSize;
    this.totalPages = totalPages;
  }
}
