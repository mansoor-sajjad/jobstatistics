package no.nav.jobsearch.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class JobAd {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String uuid;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDate publishedDate;
}