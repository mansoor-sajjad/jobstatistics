package no.nav.jobsearch.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import no.nav.jobsearch.model.JobAd;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for JobAd entities.
 */
@Repository
public interface JobAdRepository extends CrudRepository<JobAd, String> {
  @Query(
    """
    SELECT j.published,
           SUM(CASE WHEN LOWER(j.description) LIKE '%kotlin%' THEN 1 ELSE 0 END) as kotlinCount,
           SUM(CASE WHEN LOWER(j.description) LIKE '%java%' THEN 1 ELSE 0 END) as javaCount,
           COUNT(j) as totalCount
    FROM JobAd j
    WHERE j.published >= :sixMonthsAgo
    GROUP BY j.published
    ORDER BY j.published
    """
  )
  List<Object[]> getKotlinVsJavaStats2(
    @Param("sixMonthsAgo") LocalDateTime sixMonthsAgo
  );

  Optional<JobAd> findByUuid(String uuid);

  @Query("SELECT MAX(j.updated) FROM JobAd j")
  Optional<LocalDateTime> findNewestUpdatedDate();
}
