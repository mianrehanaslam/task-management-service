package com.servicemarket.taskmanagement.service;

import com.servicemarket.taskmanagement.dto.TaskRequest;
import com.servicemarket.taskmanagement.dto.TaskResponse;
import com.servicemarket.taskmanagement.entity.Task;
import com.servicemarket.taskmanagement.exception.TaskNotFoundException;
import com.servicemarket.taskmanagement.repository.TaskRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        Instant now = now();
        Task task = new Task();
        applyEditableFields(task, request);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return TaskResponse.from(taskRepository.save(task));
    }

    public List<TaskResponse> findAll() {
        return taskRepository.findAll(Sort.by("id")).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public TaskResponse findById(Long id) {
        return TaskResponse.from(getTask(id));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = getTask(id);
        applyEditableFields(task, request);
        task.setUpdatedAt(now());
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.delete(getTask(id));
    }

    private Task getTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private static void applyEditableFields(Task task, TaskRequest request) {
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.statusValue());
    }

    // H2 stores TIMESTAMP(6); truncating keeps the returned value identical to what a later read returns.
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
