package com.servicemarket.taskmanagement;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

class TaskManagementApplicationTest {

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @ActiveProfiles("local")
    class LocalProfile {

        @Autowired
        private TestRestTemplate restTemplate;

        @Test
        void startsAndExposesH2Console() {
            assertThat(restTemplate.getForEntity("/h2-console/", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(restTemplate.getForEntity("/api/tasks", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class DefaultProfile {

        @Autowired
        private TestRestTemplate restTemplate;

        @Test
        void doesNotExposeH2Console() {
            assertThat(restTemplate.getForEntity("/h2-console/", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
