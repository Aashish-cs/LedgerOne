package com.ledgerone.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class RenderDatabaseUrlEnvironmentPostProcessorTest {
    private final RenderDatabaseUrlEnvironmentPostProcessor processor = new RenderDatabaseUrlEnvironmentPostProcessor();

    @Test
    void convertsRenderPostgresUrlToSpringDatasourceProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://ledgerone:p%40ssword@dpg-example:5432/ledgerone");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-example:5432/ledgerone");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("ledgerone");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p@ssword");
    }
}
