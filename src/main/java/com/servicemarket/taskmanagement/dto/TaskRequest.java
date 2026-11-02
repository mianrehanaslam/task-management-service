package com.servicemarket.taskmanagement.dto;

import com.servicemarket.taskmanagement.entity.Task;
import com.servicemarket.taskmanagement.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for both create and full-replacement update. Unknown properties such as
 * {@code id} or {@code createdAt} are ignored, so clients cannot set system-generated fields.
 *
 * <p>{@code status} is bound as a String and checked by bean validation (rather than bound
 * straight to {@link TaskStatus}) so an invalid status is reported together with every other
 * invalid field instead of aborting deserialization on its own.
 */
public record TaskRequest(
        @NotBlank
        @Size(max = Task.TITLE_MAX_LENGTH)
        String title,

        @Size(max = Task.DESCRIPTION_MAX_LENGTH)
        String description,

        @NotNull
        @Pattern(regexp = "PENDING|IN_PROGRESS|COMPLETED", message = "must be one of: PENDING, IN_PROGRESS, COMPLETED")
        String status) {

    public TaskStatus statusValue() {
        return TaskStatus.valueOf(status);
    }
}
