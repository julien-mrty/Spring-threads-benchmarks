package com.jm.runner;

import com.jm.runner.config.RunnerProperties;
import com.github.dockerjava.api.DockerClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunnerApplicationIT {

    @Autowired TestRestTemplate rest;

    @Test
    void startRun_endpoint_returns_202() {
        var json = """
            {"script":"ok.js","params":{"BASE_URL":"http://backend:8080"}}
        """;

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var resp = rest.postForEntity("/runs",
                new HttpEntity<>(json, headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void list_returns_200_with_json() {
        var resp = rest.getForEntity("/runs", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Just check it's valid JSON array (you can make stronger assertions later with JsonPath)
        assertThat(resp.getBody()).contains("[");
    }

    @Test
    void get_missing_returns_404_problem() {
        var resp = rest.getForEntity("/runs/nope", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).contains("Run not found");
    }

    @Test
    void post_enqueues_returns_202() {
        var json = """
            {"script":"ok.js","params":{"BASE_URL":"http://backend:8080"}}
        """;

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var resp = rest.postForEntity("/runs",
                new HttpEntity<>(json, headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody()).contains("\"script\":\"ok.js\"");
    }

    @Test
    void post_invalid_returns_400_problem() {
        var json = """
            {"script":"missing.js","params":{"BASE_URL":"http://backend:8080"}}
        """;

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var resp = rest.postForEntity("/runs",
                new HttpEntity<>(json, headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("script not found");
    }
}
