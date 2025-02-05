package no.nav.jobsearch.repository;

import no.nav.jobsearch.model.JobAd;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JobAdRepository extends CrudRepository<JobAd, Long> {

    @Query("""
    SELECT FUNCTION('DATE_TRUNC', 'week', j.publishedDate) AS week,
           SUM(CASE WHEN LOWER(j.description) LIKE '%kotlin%' THEN 1 ELSE 0 END) AS kotlinCount,
           SUM(CASE WHEN LOWER(j.description) LIKE '%java%' THEN 1 ELSE 0 END) AS javaCount
    FROM JobAd j
    WHERE j.publishedDate >= :sixMonthsAgo
    GROUP BY week
    ORDER BY week
    """)
    List<Object[]> getKotlinVsJavaStats(@Param("sixMonthsAgo") LocalDate sixMonthsAgo);}