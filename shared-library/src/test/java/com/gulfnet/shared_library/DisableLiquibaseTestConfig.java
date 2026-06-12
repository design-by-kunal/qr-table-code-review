package com.gulfnet.shared_library;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class DisableLiquibaseTestConfig {

    @Bean
    @Primary
    public SpringLiquibase liquibase() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setShouldRun(false); // disables Liquibase
        return liquibase;
    }
}

