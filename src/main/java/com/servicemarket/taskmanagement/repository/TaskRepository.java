package com.servicemarket.taskmanagement.repository;

import com.servicemarket.taskmanagement.entity.Task;
import com.servicemarket.taskmanagement.entity.TaskStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status, Sort sort);
}
