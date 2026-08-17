package com.photogai.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 客户库真链路集成测试：建客户 → 列表 → 详情 → 更新 → 删除（全程带真实 JWT）。
 */
class CustomerIntegrationTest extends AbstractIntegrationTest {

    private long createCustomer(String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "客户" + System.nanoTime(),
                "phone", "1380000" + (int) (Math.random() * 10000),
                "wechatId", "wx_" + System.nanoTime()));
        String resp = mockMvc.perform(post(BASE + "/customers")
                        .headers(authHeaders(token)).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        String token = registerAndLogin();
        long id = createCustomer(token);

        mockMvc.perform(get(BASE + "/customers").headers(authHeaders(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get(BASE + "/customers/" + id).headers(authHeaders(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        String newName = "客户B_" + System.nanoTime();
        String upBody = objectMapper.writeValueAsString(Map.of("name", newName));
        mockMvc.perform(put(BASE + "/customers/" + id)
                        .headers(authHeaders(token)).content(upBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(newName));

        mockMvc.perform(delete(BASE + "/customers/" + id).headers(authHeaders(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
