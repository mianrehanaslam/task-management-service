package com.servicemarket.taskmanagement.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("Task with ID " + id + " was not found");
    }
}
