package com.servicemarket.taskmanagement.exception;

import com.servicemarket.taskmanagement.config.ClockConfig;
import com.servicemarket.taskmanagement.controller.TaskController;
import com.servicemarket.taskmanagement.entity.Task;
import com.servicemarket.taskmanagement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(ClockConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @Test
    void unexpectedErrorReturns500WithoutLeakingInternalDetails() throws Exception {
        when(taskService.findAll(null)).thenThrow(
                new IllegalStateException("jdbc:h2:mem:secret password=hunter2"));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/api/tasks"))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.servicemarket"))));
    }

    @Test
    void concurrentModificationReturns409() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(Task.class, 5L))
                .when(taskService).delete(5L);

        mockMvc.perform(delete("/api/tasks/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Task was modified or deleted by another request; please retry"))
                .andExpect(jsonPath("$.path").value("/api/tasks/5"));
    }
}
