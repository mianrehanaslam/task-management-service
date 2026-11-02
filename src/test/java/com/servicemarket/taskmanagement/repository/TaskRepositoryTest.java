package com.servicemarket.taskmanagement.repository;

import com.servicemarket.taskmanagement.entity.Task;
import com.servicemarket.taskmanagement.entity.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class TaskRepositoryTest {

    private static final Instant CREATED = Instant.parse("2026-11-02T10:30:00.123456Z");

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsTaskInH2WithGeneratedIdAndReadsItBack() {
        Task first = taskRepository.saveAndFlush(newTask("First"));
        Task second = taskRepository.saveAndFlush(newTask("Second"));
        entityManager.clear();

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isGreaterThan(first.getId());

        Task reloaded = taskRepository.findById(first.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("First");
        assertThat(reloaded.getDescription()).isEqualTo("Details");
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reloaded.getCreatedAt()).isEqualTo(CREATED);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void storesStatusAsItsName() {
        Task saved = taskRepository.saveAndFlush(newTask("Status"));

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("SELECT status FROM tasks WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        assertThat(stored).isEqualTo("IN_PROGRESS");
    }

    @Test
    void deletesTask() {
        Task saved = taskRepository.saveAndFlush(newTask("Gone"));

        taskRepository.delete(saved);
        taskRepository.flush();

        assertThat(taskRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void rejectsUpdateBasedOnStaleVersion() {
        Long id = taskRepository.saveAndFlush(newTask("Original")).getId();
        entityManager.clear();
        Task firstReader = taskRepository.findById(id).orElseThrow();
        entityManager.clear();
        Task secondReader = taskRepository.findById(id).orElseThrow();
        entityManager.clear();

        firstReader.setTitle("First writer wins");
        taskRepository.saveAndFlush(firstReader);
        entityManager.clear();

        secondReader.setTitle("Lost update");
        assertThatThrownBy(() -> taskRepository.saveAndFlush(secondReader))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        entityManager.clear();
        assertThat(taskRepository.findById(id).orElseThrow().getTitle()).isEqualTo("First writer wins");
    }

    private static Task newTask(String title) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription("Details");
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setCreatedAt(CREATED);
        task.setUpdatedAt(CREATED);
        return task;
    }
}
