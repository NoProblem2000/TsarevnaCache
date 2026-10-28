package io.github.noproblem2000.tsarevnacache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the TsarevnaCache admin service.
 *
 * <p>Spring Boot's {@link HazelcastAutoConfiguration} is excluded on purpose: it may start an
 * embedded Hazelcast member, while this service must only ever connect to an existing cluster as a
 * client (see {@code HazelcastClientConfig}).
 */
@SpringBootApplication(exclude = HazelcastAutoConfiguration.class)
@ConfigurationPropertiesScan
public class TsarevnaCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(TsarevnaCacheApplication.class, args);
    }
}
