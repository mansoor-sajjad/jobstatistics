package no.nav.jobsearch.repository;

import no.nav.jobsearch.model.JobAd;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobAdRepository extends CrudRepository<JobAd, String> {

    @Query("""
    SELECT FUNCTION('DATE_TRUNC', 'week', j.published) AS week,
           SUM(CASE WHEN LOWER(j.description) LIKE '%kotlin%' THEN 1 ELSE 0 END) AS kotlinCount,
           SUM(CASE WHEN LOWER(j.description) LIKE '%java%' THEN 1 ELSE 0 END) AS javaCount
    FROM JobAd j
    WHERE j.published >= :sixMonthsAgo
    GROUP BY week
    ORDER BY week
    """)
    List<Object[]> getKotlinVsJavaStats(@Param("sixMonthsAgo") LocalDateTime sixMonthsAgo);

    @Transactional
    default JobAd updateOrInsert(JobAd jobAd) {
        return save(jobAd);
    }

    Optional<JobAd> findByUuid(String uuid);

    @Query("SELECT MAX(j.published) FROM JobAd j")
    Optional<LocalDateTime> findNewestUpdatedDate();
}