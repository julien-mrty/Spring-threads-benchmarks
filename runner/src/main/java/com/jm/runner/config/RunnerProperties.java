package com.jm.runner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("runner")
public class RunnerProperties {
    private final String k6Container;
    private final String allowBaseUrl;
    private final String promRemoteWriteUrl;
    private final String scriptsDir;
    private final String resultsDir;

    public RunnerProperties(
            String k6Container,
            String allowBaseUrl,
            String promRemoteWriteUrl,
            String scriptsDir,
            String resultsDir
    ) {
        this.k6Container = k6Container != null ? k6Container : "k6";
        this.allowBaseUrl = allowBaseUrl != null ? allowBaseUrl : "http://backend:8080";
        this.promRemoteWriteUrl = promRemoteWriteUrl != null ? promRemoteWriteUrl : "http://prometheus:9090/api/v1/write";
        this.scriptsDir = scriptsDir != null ? scriptsDir : "/work";
        this.resultsDir = resultsDir != null ? resultsDir : "/data/runs";
    }

    public String getScriptsDir() {
        return scriptsDir;
    }
    public String getAllowBaseUrl() {
        return allowBaseUrl;
    }
    public String getResultsDir() {
        return resultsDir;
    }
    public String getPromRemoteWriteUrl() {
        return promRemoteWriteUrl;
    }
    public String getK6Container() {
        return k6Container;
    }
}
