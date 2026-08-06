package com.photogai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档元信息（Knife4j/Swagger 可选启用，仅描述用）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI photogAiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("摄影师的 AI 接单跟单助手 API")
                .description("阶段1 MVP（P0）：订单 / 档期 / AI 报价 / 客户库 / 认证 / 额度")
                .version("v1.0"));
    }
}
