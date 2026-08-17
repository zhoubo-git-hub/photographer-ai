package com.photogai.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * AI 报价真链路集成测试：无 DeepSeek key 时走规则价降级，验证端到端仍返回 200 + 报价区间。
 */
class QuoteIntegrationTest extends AbstractIntegrationTest {

    @Test
    void quote_fallbackRulePrice() throws Exception {
        String token = registerAndLogin();
        String body = objectMapper.writeValueAsString(Map.of(
                "shootType", "wedding",
                "durationHours", 4,
                "photoCount", 200,
                "region", "上海",
                "style", "复古"));
        mockMvc.perform(post(BASE + "/ai/quote")
                        .headers(authHeaders(token)).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.priceLow").exists())
                .andExpect(jsonPath("$.data.priceHigh").exists())
                .andExpect(jsonPath("$.data.script").exists());
    }
}
