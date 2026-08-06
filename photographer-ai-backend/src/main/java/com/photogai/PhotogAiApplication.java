package com.photogai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 摄影师 AI 接单跟单助手 - 后端启动类。
 *
 * <p>统一前缀 {@code /api}；除 {@code /api/auth/**} 外均须 Bearer JWT。
 * 多租户按 {@code studio_id} 隔离，所有业务查询强制带上当前用户所属 studio。
 * {@code @EnableScheduling} 启用阶段2 复购引擎的 {@code @Scheduled} 定时扫描。
 */
@SpringBootApplication
@EnableScheduling
public class PhotogAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhotogAiApplication.class, args);
    }
}
