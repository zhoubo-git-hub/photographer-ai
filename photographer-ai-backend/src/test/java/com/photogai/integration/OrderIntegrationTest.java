package com.photogai.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 订单真链路集成测试：先建客户，再建订单 → 列表 → 详情 → 状态流转（CONSULT→DEPOSIT）。
 */
class OrderIntegrationTest extends AbstractIntegrationTest {

    private long createCustomer(String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "订单客户" + System.nanoTime()));
        String resp = mockMvc.perform(post(BASE + "/customers")
                        .headers(authHeaders(token)).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    @Test
    void createListDetailAndChangeStatus() throws Exception {
        String token = registerAndLogin();
        long customerId = createCustomer(token);

        String orderBody = objectMapper.writeValueAsString(Map.of(
                "customerId", customerId,
                "title", "婚礼跟拍" + System.nanoTime(),
                "shootType", "wedding",
                "amount", 5000,
                "currency", "CNY"));
        String resp = mockMvc.perform(post(BASE + "/orders")
                        .headers(authHeaders(token)).content(orderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(resp).path("data").path("id").asLong();

        mockMvc.perform(get(BASE + "/orders").headers(authHeaders(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get(BASE + "/orders/" + orderId).headers(authHeaders(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId));

        String statusBody = objectMapper.writeValueAsString(Map.of("toStatus", "DEPOSIT"));
        mockMvc.perform(post(BASE + "/orders/" + orderId + "/status")
                        .headers(authHeaders(token)).content(statusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DEPOSIT"));
    }
}
