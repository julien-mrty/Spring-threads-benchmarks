package com.jm.spring_threads_benchmarks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.jm")
public class SpringThreadsBenchmarksApplication {
	public static void main(String[] args) {
        SpringApplication.run(SpringThreadsBenchmarksApplication.class, args);
	}
}
