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
 * 计费真链路集成测试：订阅下单（mock 支付）→ 模拟支付成功 → 查询订阅状态。
 */
class BillingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void subscribeMockPayAndQuery() throws Exception {
        String token = registerAndLogin();
        String subBody = objectMapper.writeValueAsString(Map.of("planType", "PRO", "channel", "MOCK"));
        String resp = mockMvc.perform(post(BASE + "/billing/subscribe")
                        .headers(authHeaders(token)).content(subBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.outTradeNo").exists())
                .andReturn().getResponse().getContentAsString();
        String outTradeNo = objectMapper.readTree(resp).path("data").path("outTradeNo").asText();

        String payBody = objectMapper.writeValueAsString(Map.of("outTradeNo", outTradeNo));
        mockMvc.perform(post(BASE + "/billing/mock-pay")
                        .headers(authHeaders(token)).content(payBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get(BASE + "/billing/subscription").headers(authHeaders(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }
}
