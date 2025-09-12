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
        // --- POST /orders ---
        var json = """
            {"customer":"it-user","totalCents":1234}
            """;
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<OrderDto> createResp =
                rest.postForEntity("/orders", new HttpEntity<>(json, headers), OrderDto.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody()).isNotNull();
        var created = createResp.getBody();

        assertThat(created.customer()).isEqualTo("it-user");
        assertThat(created.totalCents()).isEqualTo(1234);
        assertThat(created.id()).isPositive();

        // --- GET /orders/{id} ---
        ResponseEntity<OrderDto> getResp =
                rest.getForEntity("/orders/" + created.id(), OrderDto.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody()).isNotNull();
        var fetched = getResp.getBody();

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.customer()).isEqualTo("it-user");
        assertThat(fetched.totalCents()).isEqualTo(1234);
    }
}

