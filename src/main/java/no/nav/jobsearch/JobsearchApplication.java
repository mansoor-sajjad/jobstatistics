package no.nav.jobsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobsearchApplication {

  public static void main(String[] args) {
    SpringApplication.run(JobsearchApplication.class, args);
  }
}
