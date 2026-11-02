package com.servicemarket.taskmanagement.service;

import com.servicemarket.taskmanagement.dto.TaskRequest;
import com.servicemarket.taskmanagement.dto.TaskResponse;
import com.servicemarket.taskmanagement.entity.Task;
import com.servicemarket.taskmanagement.entity.TaskStatus;
import com.servicemarket.taskmanagement.exception.TaskNotFoundException;
import com.servicemarket.taskmanagement.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    // Nanosecond component proves timestamps are truncated to the DB's microsecond precision.
    private static final Instant NOW = Instant.parse("2026-11-02T10:30:00.123456789Z");
    private static final Instant NOW_TRUNCATED = Instant.parse("2026-11-02T10:30:00.123456Z");
    private static final Instant EARLIER = Instant.parse("2026-11-01T08:00:00Z");

    @Mock
    private TaskRepository taskRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createSetsTimestampsAndReturnsSavedTask() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(1L);
            return task;
        });

        TaskResponse response = taskService.create(
                new TaskRequest("Write docs", "Setup and usage", "PENDING"));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getTitle()).isEqualTo("Write docs");
        assertThat(saved.getDescription()).isEqualTo("Setup and usage");
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW_TRUNCATED);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW_TRUNCATED);

        assertThat(response).isEqualTo(new TaskResponse(
                1L, "Write docs", "Setup and usage", TaskStatus.PENDING, NOW_TRUNCATED, NOW_TRUNCATED));
    }

    @Test
    void findAllReturnsTasksOrderedById() {
        Task first = task(1L, "First");
        Task second = task(2L, "Second");
        when(taskRepository.findAll(Sort.by("id"))).thenReturn(List.of(first, second));

        List<TaskResponse> responses = taskService.findAll(null);

        assertThat(responses).extracting(TaskResponse::id).containsExactly(1L, 2L);
        verify(taskRepository, never()).findByStatus(any(), any());
    }

    @Test
    void findAllReturnsEmptyListWhenNoTasksExist() {
        when(taskRepository.findAll(Sort.by("id"))).thenReturn(List.of());

        assertThat(taskService.findAll(null)).isEmpty();
    }

    // DS-6623: filtering must happen in the repository query, not in memory.
    @Test
    void findAllWithStatusQueriesRepositoryByStatus() {
        Task pending = task(1L, "Pending");
        when(taskRepository.findByStatus(TaskStatus.PENDING, Sort.by("id"))).thenReturn(List.of(pending));

        List<TaskResponse> responses = taskService.findAll(TaskStatus.PENDING);

        assertThat(responses).extracting(TaskResponse::id).containsExactly(1L);
        verify(taskRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void findAllWithStatusReturnsEmptyListWhenNothingMatches() {
        when(taskRepository.findByStatus(TaskStatus.COMPLETED, Sort.by("id"))).thenReturn(List.of());

        assertThat(taskService.findAll(TaskStatus.COMPLETED)).isEmpty();
    }

    @Test
    void findByIdReturnsMatchingTask() {
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task(7L, "Seven")));

        TaskResponse response = taskService.findById(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("Seven");
    }

    @Test
    void findByIdThrowsWhenTaskDoesNotExist() {
        when(taskRepository.findById(25L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(25L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with ID 25 was not found");
    }

    @Test
    void updateReplacesEditableFieldsAndPreservesIdAndCreatedAt() {
        Task existing = task(3L, "Old title");
        when(taskRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(taskRepository.saveAndFlush(existing)).thenReturn(existing);

        TaskResponse response = taskService.update(3L,
                new TaskRequest("New title", null, "COMPLETED"));

        assertThat(response).isEqualTo(new TaskResponse(
                3L, "New title", null, TaskStatus.COMPLETED, EARLIER, NOW_TRUNCATED));
    }

    @Test
    void updateThrowsWhenTaskDoesNotExist() {
        when(taskRepository.findById(25L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(25L,
                new TaskRequest("Title", null, "PENDING")))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteRemovesExistingTask() {
        Task existing = task(4L, "Delete me");
        when(taskRepository.findById(4L)).thenReturn(Optional.of(existing));

        taskService.delete(4L);

        verify(taskRepository).delete(existing);
    }

    @Test
    void deleteThrowsWhenTaskDoesNotExist() {
        when(taskRepository.findById(25L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(25L)).isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).delete(any());
    }

    private static Task task(Long id, String title) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setDescription("Description of " + title);
        task.setStatus(TaskStatus.PENDING);
        task.setCreatedAt(EARLIER);
        task.setUpdatedAt(EARLIER);
        return task;
    }
}
