package com.jm.runner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data                 // generates getters/setters, toString, equals/hashCode
@NoArgsConstructor    // needed for binder (JavaBean style)
@ConfigurationProperties("runner")
public class RunnerProperties {
    private String k6Container = "k6";
    private String allowBaseUrl = "http://backend:8080";
    private String promRemoteWriteUrl = "http://prometheus:9090/api/v1/write";
    private String scriptsDir = "/work";
    private String resultsDir = "/data/runs";
    private int maxConcurrency = 1;

}
