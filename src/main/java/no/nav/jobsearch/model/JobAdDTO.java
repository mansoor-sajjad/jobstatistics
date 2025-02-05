package no.nav.jobsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobAdDTO {
    private String uuid;
    private String title;
    private String description;
    private String published;
}