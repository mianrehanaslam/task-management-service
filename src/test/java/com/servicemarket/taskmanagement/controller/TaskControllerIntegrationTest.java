package com.servicemarket.taskmanagement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicemarket.taskmanagement.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {

    private static final String VALID_CREATE = """
            {"title": "Complete project documentation",
             "description": "Add setup and API usage instructions",
             "status": "PENDING"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
    }

    // AC1
    @Test
    void createReturns201WithGeneratedIdAndTimestampsAndPersistsInH2() throws Exception {
        JsonNode created = readJson(mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/tasks/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Complete project documentation"))
                .andExpect(jsonPath("$.description").value("Add setup and API usage instructions"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString()));

        long id = created.get("id").asLong();
        assertThat(created.get("createdAt").asText()).isEqualTo(created.get("updatedAt").asText());
        assertThat(taskRepository.findById(id)).isPresent();
    }

    @Test
    void createIgnoresClientSuppliedIdAndTimestamps() throws Exception {
        JsonNode created = readJson(mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 999, "createdAt": "2000-01-01T00:00:00Z",
                                 "title": "Sneaky", "status": "PENDING"}"""))
                .andExpect(status().isCreated()));

        assertThat(created.get("id").asLong()).isNotEqualTo(999L);
        assertThat(created.get("createdAt").asText()).doesNotStartWith("2000");
    }

    @Test
    void createWithoutDescriptionIsAccepted() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"No description\", \"status\": \"COMPLETED\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"description\":null")));
    }

    @Test
    void createAcceptsFieldsExactlyAtMaximumLength() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("t".repeat(150), "d".repeat(1000), "PENDING")))
                .andExpect(status().isCreated());
    }

    // AC2
    @Test
    void createWithBlankTitleReturns400WithFieldError() throws Exception {
        expectValidationError(post("/api/tasks"), body("   ", null, "PENDING"), "title");
    }

    @Test
    void createWithMissingTitleReturns400WithFieldError() throws Exception {
        expectValidationError(post("/api/tasks"), "{\"status\": \"PENDING\"}", "title");
    }

    @Test
    void createWithMissingStatusReturns400WithFieldError() throws Exception {
        expectValidationError(post("/api/tasks"), "{\"title\": \"No status\"}", "status");
    }

    @Test
    void createWithInvalidStatusReturns400WithFieldError() throws Exception {
        expectValidationError(post("/api/tasks"), body("Title", null, "DONE"), "status")
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value(containsString("PENDING, IN_PROGRESS, COMPLETED")));
    }

    @Test
    void createWithLowercaseStatusReturns400WithFieldError() throws Exception {
        expectValidationError(post("/api/tasks"), body("Title", null, "pending"), "status");
    }

    // Regression: an invalid status must not hide other invalid fields.
    @Test
    void createWithBlankTitleAndInvalidStatusReportsBothFields() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body(" ", null, "DONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("title", "status")));
    }

    @Test
    void createWithWrongJsonTypeReturns400WithFieldError() throws Exception {
        expectValidationError(post("/api/tasks"), "{\"title\": {\"x\": 1}, \"status\": \"PENDING\"}", "title")
                .andExpect(jsonPath("$.fieldErrors[0].message").value("has an invalid type"));
    }

    @Test
    void createWithOverLimitTitleReturns400WithFieldError() throws Exception {
        expectValidationError(post("/api/tasks"), body("t".repeat(151), null, "PENDING"), "title");
    }

    @Test
    void createWithOverLimitDescriptionReturns400WithFieldError() throws Exception {
        expectValidationError(post("/api/tasks"), body("Title", "d".repeat(1001), "PENDING"), "description");
    }

    @Test
    void createReportsEveryInvalidField() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "d".repeat(1001), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("title", "description", "status")));
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void createWithMalformedJsonReturns400WithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.path").value("/api/tasks"))
                .andExpect(content().string(not(containsString("jackson"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void createWithUnsupportedMediaTypeReturns415WithConsistentError() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.path").value("/api/tasks"));
    }

    @Test
    void createWithEmptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void createWithJsonNullBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // AC3
    @Test
    void listReturnsAllStoredTasks() throws Exception {
        long first = createTask("First");
        long second = createTask("Second");

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[1].id").value(second))
                .andExpect(jsonPath("$[1].title").value("Second"));
    }

    // AC4
    @Test
    void listReturnsEmptyArrayWhenNoTasksExist() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    // AC5
    @Test
    void getByIdReturnsMatchingTask() throws Exception {
        long id = createTask("Find me");

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Find me"));
    }

    // AC6 + error-response structure
    @Test
    void getByIdReturns404WithConsistentErrorResponse() throws Exception {
        mockMvc.perform(get("/api/tasks/25"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Task with ID 25 was not found"))
                .andExpect(jsonPath("$.path").value("/api/tasks/25"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void nonNumericIdReturns400WithConsistentError() throws Exception {
        mockMvc.perform(get("/api/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"))
                .andExpect(jsonPath("$.path").value("/api/tasks/abc"));
    }

    // AC7 + AC9
    @Test
    void updateReplacesEditableFieldsAndPreservesIdAndCreatedAt() throws Exception {
        JsonNode created = readJson(mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE)));
        long id = created.get("id").asLong();

        JsonNode updated = readJson(mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Complete API documentation", "Add request and response examples",
                                "IN_PROGRESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Complete API documentation"))
                .andExpect(jsonPath("$.description").value("Add request and response examples"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS")));

        assertThat(updated.get("createdAt").asText()).isEqualTo(created.get("createdAt").asText());
        assertThat(Instant.parse(updated.get("updatedAt").asText()))
                .isAfter(Instant.parse(created.get("updatedAt").asText()));

        // AC9: a subsequent read returns the latest values stored in H2.
        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Complete API documentation"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()))
                .andExpect(jsonPath("$.updatedAt").value(updated.get("updatedAt").asText()));
    }

    @Test
    void updateWithoutDescriptionClearsIt() throws Exception {
        long id = createTask("Has description");

        mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Has description\", \"status\": \"PENDING\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"description\":null")));
    }

    @Test
    void updateReturns404WhenTaskDoesNotExist() throws Exception {
        mockMvc.perform(put("/api/tasks/25")
                        .contentType(MediaType.APPLICATION_JSON).content(body("Title", null, "PENDING")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task with ID 25 was not found"))
                .andExpect(jsonPath("$.path").value("/api/tasks/25"));
    }

    @Test
    void updateWithInvalidDataReturns400AndLeavesTaskUnchanged() throws Exception {
        long id = createTask("Unchanged");

        expectValidationError(put("/api/tasks/" + id), body(" ", null, "PENDING"), "title");
        expectValidationError(put("/api/tasks/" + id), body("Title", null, "UNKNOWN"), "status");

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(jsonPath("$.title").value("Unchanged"));
    }

    // AC8
    @Test
    void deleteRemovesTaskAndReturns204() throws Exception {
        long id = createTask("Delete me");

        mockMvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(taskRepository.findById(id)).isEmpty();
        mockMvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns404WhenTaskDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/tasks/25"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task with ID 25 was not found"));
    }

    @Test
    void unsupportedMethodReturns405WithConsistentError() throws Exception {
        mockMvc.perform(delete("/api/tasks"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.path").value("/api/tasks"));
    }

    // Regression: a browser-style Accept header must not turn a 404 into an HTML 500.
    @Test
    void notFoundIsReturnedAsJsonForBrowserAcceptHeader() throws Exception {
        mockMvc.perform(get("/api/tasks/25")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Task with ID 25 was not found"));
    }

    @Test
    void htmlOnlyAcceptReturns406AsJson() throws Exception {
        mockMvc.perform(get("/api/tasks/25").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406));
    }

    @Test
    void unsupportedAcceptTypeReturns406AsJson() throws Exception {
        mockMvc.perform(get("/api/tasks").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406));
    }

    @Test
    void unknownRouteReturns404WithFixedMessage() throws Exception {
        mockMvc.perform(get("/api/taskz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Request could not be processed: Not Found"))
                .andExpect(jsonPath("$.path").value("/api/taskz"));
    }

    private ResultActions expectValidationError(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String content, String field) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").isString())
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].message").isString());
    }

    private long createTask(String title) throws Exception {
        return readJson(mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body(title, "Details", "PENDING")))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String body(String title, String description, String status) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("title", title);
        node.put("description", description);
        node.put("status", status);
        return objectMapper.writeValueAsString(node);
    }
}
