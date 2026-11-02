package com.servicemarket.taskmanagement.dto;

import com.servicemarket.taskmanagement.entity.TaskStatus;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRequestTest {

    // Guards against the status regex drifting from the TaskStatus enum when a value is added.
    @Test
    void statusPatternAcceptsExactlyTheTaskStatusValues() throws Exception {
        Pattern pattern = TaskRequest.class.getDeclaredField("status").getAnnotation(Pattern.class);
        List<String> enumValues = Arrays.stream(TaskStatus.values()).map(Enum::name).toList();

        assertThat(pattern.regexp().split("\\|")).containsExactlyInAnyOrderElementsOf(enumValues);
        assertThat(pattern.message()).isEqualTo("must be one of: " + String.join(", ", enumValues));
    }

    @Test
    void statusValueMapsToEnum() {
        assertThat(new TaskRequest("Title", null, "IN_PROGRESS").statusValue()).isEqualTo(TaskStatus.IN_PROGRESS);
    }
}
