package com.photogai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真链路集成测试基类：启动完整 Spring 上下文 + MockMvc，打真实 controller 端点
 * → service → repository → 真 PostgreSQL（见 application-test.yml，本地 photogai_it / CI photogai）。
 *
 * <p>@Transactional 让每个测试方法在事务内运行，方法结束回滚，DB 隔离；
 * 同一方法内 register→login→查询 共享同一事务，数据可见。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static final String BASE = "/api";

    /** 注册新工作室+用户并登录，返回真实 JWT（走完整 JwtFilter + SecurityConfig + CurrentUser 链路）。*/
    protected String registerAndLogin() throws Exception {
        String username = "it_" + System.nanoTime() + "_" + (int) (Math.random() * 1_000_000);
        String password = "Passw0rd!";
        String regBody = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password,
                "email", username + "@it.com",
                "studioName", "IT Studio " + username));
        String regResp = mockMvc.perform(post(BASE + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(regResp).path("data").path("token").asText();
        if (token == null || token.isBlank()) {
            String loginBody = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
            String loginResp = mockMvc.perform(post(BASE + "/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            token = objectMapper.readTree(loginResp).path("data").path("token").asText();
        }
        return token;
    }

    /** 携带真实 JWT 的请求头（Authorization: Bearer + Content-Type: application/json）。*/
    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
