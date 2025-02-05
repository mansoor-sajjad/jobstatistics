package no.nav.jobsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobFeedResponse {
    private List<JobAdDTO> content;
}