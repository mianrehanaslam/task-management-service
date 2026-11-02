package com.servicemarket.taskmanagement.dto;

import com.servicemarket.taskmanagement.entity.Task;
import com.servicemarket.taskmanagement.entity.TaskStatus;

import java.time.Instant;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
