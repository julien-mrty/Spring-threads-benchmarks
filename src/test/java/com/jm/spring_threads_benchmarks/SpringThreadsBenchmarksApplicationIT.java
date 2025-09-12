package com.jm.spring_threads_benchmarks;

import com.jm.spring_threads_benchmarks.dto.OrderDto;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.boot.test.web.client.TestRestTemplate;

import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SpringThreadsBenchmarksApplicationIT {

    @Container
    static final PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("appdb")
                    .withUsername("app")
                    .withPassword("app_pw");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.threads.virtual.enabled", () -> "true"); // test VT profile behavior
        // Flyway enabled by default; it will run V1__init.sql, V2__seed.sql
    }

    @Autowired TestRestTemplate rest;

    @Test
    void slowEndpointWorks() {
        var resp = rest.getForEntity("/orders/report/slow/300", String.class);
        org.assertj.core.api.Assertions.assertThat(resp.getStatusCode().value()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(resp.getBody()).isEqualTo("slept 300 ms");
    }

    @Test
    void create_then_get_by_id() {
        var body = new org.springframework.util.LinkedMultiValueMap<String,String>(); // (simpler: use String JSON)
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var json = "{\"customer\":\"it-user\",\"totalCents\":1234}";

        var created = rest.postForEntity("/orders",
                new org.springframework.http.HttpEntity<>(json, headers),
                OrderDto.class);

        org.assertj.core.api.Assertions.assertThat(created.getStatusCode().value()).isEqualTo(201);
        var id = created.getBody().id();

        var fetched = rest.getForEntity("/orders/" + id, OrderDto.class);
        org.assertj.core.api.Assertions.assertThat(fetched.getStatusCode().value()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(fetched.getBody().customer()).isEqualTo("it-user");
        org.assertj.core.api.Assertions.assertThat(fetched.getBody().totalCents()).isEqualTo(1234);
    }
}

