package dev.ted.jitterticket;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * Standalone launcher: populates the database using
 * {@link BatchedLargeSampleDataPopulator} and then exits.
 * <p>
 * Run this class's main() (from the IDE, or with
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.main-class=dev.ted.jitterticket.BatchedLargeSampleDataPopulatorApplication}).
 * <p>
 * It deliberately does NOT component-scan the main application, so no projections,
 * processors, or web layer are created; only the DataSource, JdbcTemplate, and
 * TransactionTemplate that the populator needs (plus schema.sql initialization).
 * Not annotated with @Configuration/@SpringBootApplication, so the main
 * application's component scan will not pick it up.
 */
@EnableAutoConfiguration
@Import(BatchedLargeSampleDataPopulator.class)
public class BatchedLargeSampleDataPopulatorApplication {

    public static void main(String[] args) {
        // devtools' restart classloader isn't wanted for a run-once job
        System.setProperty("spring.devtools.restart.enabled", "false");
        ConfigurableApplicationContext context =
                new SpringApplicationBuilder(BatchedLargeSampleDataPopulatorApplication.class)
                        .web(WebApplicationType.NONE)
                        .run(args); // the populator is an ApplicationRunner, so it has finished when run() returns
        System.exit(SpringApplication.exit(context));
    }
}
