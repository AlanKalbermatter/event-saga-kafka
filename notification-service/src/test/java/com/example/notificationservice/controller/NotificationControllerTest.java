package com.example.notificationservice.controller;

import com.example.notificationservice.dto.EventTypeInfo;
import com.example.notificationservice.dto.NotificationStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getSupportedEventTypes_ShouldReturnEventTypesList() throws Exception {
        mockMvc.perform(get("/api/notifications/event-types"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].topicName").value("order.completed"))
                .andExpect(jsonPath("$[0].eventName").value("OrderCompleted"))
                .andExpect(jsonPath("$[1].topicName").value("order.cancelled"))
                .andExpect(jsonPath("$[1].eventName").value("OrderCancelled"));
    }

    @Test
    void getStatus_ShouldReturnServiceStatus() throws Exception {
        mockMvc.perform(get("/api/notifications/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.serviceName").value("Notification Service"))
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.monitoredTopics").isArray())
                .andExpect(jsonPath("$.monitoredTopics.length()").value(2));
    }
}
