package com.servicemarket.taskmanagement.exception;

import com.servicemarket.taskmanagement.entity.TaskStatus;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Raised when the {@code status} query parameter is unusable. The rejected value is deliberately
 * not kept, so it can never be echoed back to the client.
 */
public class InvalidTaskStatusException extends RuntimeException {

    private InvalidTaskStatusException(String message) {
        super(message);
    }

    /** The value is not one of the {@link TaskStatus} names. */
    public static InvalidTaskStatusException unsupported() {
        return new InvalidTaskStatusException("must be one of: " + Arrays.stream(TaskStatus.values())
                .map(Enum::name)
                .collect(Collectors.joining(", ")));
    }

    /** The parameter was sent more than once, e.g. {@code ?status=PENDING&status=COMPLETED}. */
    public static InvalidTaskStatusException repeated() {
        return new InvalidTaskStatusException("must be specified only once");
    }
}
