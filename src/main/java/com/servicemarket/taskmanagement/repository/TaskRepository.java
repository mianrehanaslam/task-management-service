package com.servicemarket.taskmanagement.repository;

import com.servicemarket.taskmanagement.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
