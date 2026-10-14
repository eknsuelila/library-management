package com.portfolio.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Library Management System REST API.
 *
 * <p>{@code @SpringBootApplication} combines three annotations:
 * <ul>
 *   <li>{@code @Configuration} - this class may declare beans;</li>
 *   <li>{@code @EnableAutoConfiguration} - Spring Boot configures things it finds on the classpath
 *       (a DataSource for H2/PostgreSQL, Jackson for JSON, an embedded Tomcat server, ...);</li>
 *   <li>{@code @ComponentScan} - every {@code @Component}, {@code @Service}, {@code @RestController},
 *       {@code @Configuration} ... in this package and its sub-packages is discovered and created.
 *       That is why all our classes live under {@code com.portfolio.library}.</li>
 * </ul>
 *
 * <p>Package layout is "package by feature": {@code book}, {@code loan}, {@code member}, {@code stats},
 * {@code auth}, {@code user} each hold their own controller, service, repository and DTOs, while
 * {@code common}, {@code config} and {@code security} hold cross-cutting code.
 */
@SpringBootApplication
public class LibraryApplication {

    /** Starts Spring: builds all beans, runs Flyway migrations, then starts Tomcat on the configured port. */
    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }
}
