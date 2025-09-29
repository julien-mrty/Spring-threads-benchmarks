package com.jm.runner.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.jm.runner.config.RunnerProperties;

@Configuration
@EnableConfigurationProperties(RunnerProperties.class)
public class RunnerConfig {}
