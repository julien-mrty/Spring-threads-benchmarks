package com.jm.runner.configuration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import com.jm.runner.config.RunnerProperties;

@TestConfiguration
public class TestRunnerConfiguration {

    @Bean
    RunnerProperties runnerProperties() {
        RunnerProperties props = new RunnerProperties();
        props.setAllowBaseUrl("http://backend:8080");
        props.setScriptsDir("/tmp");
        props.setResultsDir("/tmp");
        props.setMaxConcurrency(1);
        return props;
    }
}
