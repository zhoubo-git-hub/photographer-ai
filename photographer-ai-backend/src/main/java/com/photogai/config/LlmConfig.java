package com.photogai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * LLM 客户端基础设施：提供 Spring 内置 {@link RestClient}（零额外 SDK）。
 *
 * <p>模型 / 密钥 / BaseURL 全部走配置 + 环境变量（见 {@code LlmClient}），
 * 默认 DeepSeek，可切通义 / 智谱不改代码。
 */
@Configuration
public class LlmConfig {

    @Bean
    public RestClient llmRestClient() {
        return RestClient.builder().build();
    }
}
