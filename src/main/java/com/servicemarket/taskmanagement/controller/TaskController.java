package com.servicemarket.taskmanagement.controller;

import com.servicemarket.taskmanagement.dto.TaskRequest;
import com.servicemarket.taskmanagement.dto.TaskResponse;
import com.servicemarket.taskmanagement.entity.TaskStatus;
import com.servicemarket.taskmanagement.exception.InvalidTaskStatusException;
import com.servicemarket.taskmanagement.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping(path = "/api/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
        TaskResponse created = taskService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Lists all tasks, or only those matching the optional {@code status} query parameter. The
     * value is matched exactly (case-sensitive, like the request body); anything else, a blank
     * value, or a repeated parameter is rejected with {@code 400}.
     *
     * <p>The raw query map is used instead of binding {@code status} directly, because Spring's
     * String/List binding joins or splits on commas and would hide a repeated parameter.
     */
    @GetMapping
    public List<TaskResponse> findAll(@RequestParam MultiValueMap<String, String> queryParams) {
        return taskService.findAll(parseStatusFilter(queryParams.get("status")));
    }

    @GetMapping("/{id}")
    public TaskResponse findById(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static TaskStatus parseStatusFilter(List<String> values) {
        if (values == null) {
            return null;
        }
        if (values.size() > 1) {
            throw InvalidTaskStatusException.repeated();
        }
        return Arrays.stream(TaskStatus.values())
                .filter(status -> status.name().equals(values.get(0)))
                .findFirst()
                .orElseThrow(InvalidTaskStatusException::unsupported);
    }
}
