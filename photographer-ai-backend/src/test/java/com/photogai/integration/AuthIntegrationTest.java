package com.photogai.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 认证链路真链路集成测试：注册 / 登录 / 错误密码 401 / 无 token 401 自定义报文 / 带 token 受保护访问。
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void register_returnsTokenAndStudio() throws Exception {
        String username = "it_" + System.nanoTime();
        String regBody = objectMapper.writeValueAsString(Map.of(
                "username", username, "password", "Passw0rd!",
                "email", username + "@it.com", "studioName", "Studio " + username));
        mockMvc.perform(post(BASE + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.studio").exists());
    }

    @Test
    void login_withCorrectPassword_returnsToken() throws Exception {
        String username = "it_" + System.nanoTime();
        String password = "Passw0rd!";
        String regBody = objectMapper.writeValueAsString(Map.of(
                "username", username, "password", password,
                "email", username + "@it.com", "studioName", "Studio " + username));
        mockMvc.perform(post(BASE + "/auth/register").contentType(MediaType.APPLICATION_JSON).content(regBody))
                .andExpect(status().isOk());
        String loginBody = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String username = "it_" + System.nanoTime();
        String regBody = objectMapper.writeValueAsString(Map.of(
                "username", username, "password", "Passw0rd!",
                "email", username + "@it.com", "studioName", "Studio " + username));
        mockMvc.perform(post(BASE + "/auth/register").contentType(MediaType.APPLICATION_JSON).content(regBody))
                .andExpect(status().isOk());
        String loginBody = objectMapper.writeValueAsString(Map.of("username", username, "password", "WrongPass9!"));
        mockMvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401CustomMessage() throws Exception {
        mockMvc.perform(get(BASE + "/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    @Test
    void protectedEndpoint_withToken_returns200() throws Exception {
        String token = registerAndLogin();
        mockMvc.perform(get(BASE + "/customers").headers(authHeaders(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
