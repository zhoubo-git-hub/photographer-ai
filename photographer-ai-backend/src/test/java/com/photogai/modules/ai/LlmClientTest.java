package com.photogai.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.modules.ai.dto.QuoteResponse;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * LlmClient 行为测试：重点覆盖 AI 降级（IllegalStateException）分支与解析/截断分支，
 * 保证上游异常或密钥缺失时不会向用户抛 500。
 *
 * <p>RestClient 采用显式 mock（不依赖 RETURNS_DEEP_STUBS）。@Value 私有字段通过
 * ReflectionTestUtils 注入。guardPromptLength 为私有方法，用反射调用以覆盖其边界分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmClientTest {

    private static final String BASE_URL = "https://api.deepseek.com";
    private static final String API_KEY = "sk-test";
    private static final String MODEL = "deepseek-chat";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec uriSpec;
    @Mock
    private RestClient.RequestBodySpec bodySpec;
    @Mock
    private RestClient.ResponseSpec respSpec;

    private LlmClient client;

    @BeforeEach
    void setUp() {
        client = new LlmClient(restClient);
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(client, "apiKey", API_KEY);
        ReflectionTestUtils.setField(client, "model", MODEL);
    }

    // -----------------------------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------------------------

    /** 把一段 content 包成 OpenAI 兼容响应 JSON： {"choices":[{"message":{"content": ...}}]} */
    private String wrapContent(String content) {
        try {
            Map<String, Object> root = Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", content))));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构造 mock 响应失败", e);
        }
    }

    /** 接线完整 RestClient 调用链，返回最末端的 ResponseSpec 供设定返回值/异常。 */
    private RestClient.ResponseSpec stubChain() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(uriSpec);
        when(uriSpec.header(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        return respSpec;
    }

    private RestClientResponseException mockHttpError(HttpStatus status) {
        RestClientResponseException ex = mock(RestClientResponseException.class);
        when(ex.getStatusCode()).thenReturn(status);
        return ex;
    }

    private Object invokeGuard(String system, String user) throws Exception {
        Method m = LlmClient.class.getDeclaredMethod("guardPromptLength", String.class, String.class);
        m.setAccessible(true);
        return m.invoke(client, system, user);
    }

    // -----------------------------------------------------------------------------------------
    // complete()：apiKey 守卫
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("complete: apiKey 为 null 应抛出 IllegalStateException 且含『未配置』")
    void complete_apiKeyNull_throws() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        assertThatThrownBy(() -> client.complete("任意 prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("complete: apiKey 为空白串应抛出 IllegalStateException 且含『未配置』")
    void complete_apiKeyBlank_throws() {
        ReflectionTestUtils.setField(client, "apiKey", "   ");

        assertThatThrownBy(() -> client.complete("任意 prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    // -----------------------------------------------------------------------------------------
    // complete()：正常解析路径
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("complete: 合法报价 JSON 返回正确的 QuoteResponse 字段")
    void complete_validJson_returnsQuoteResponse() {
        String json = "{\"priceLow\":100,\"priceHigh\":200,\"basis\":\"人像精修耗时\",\"script\":\"您好，建议报价\"}";
        when(stubChain().body(String.class)).thenReturn(wrapContent(json));

        QuoteResponse resp = client.complete("人像精修一次多少钱");

        assertThat(resp).isNotNull();
        assertThat(resp.getPriceLow()).isEqualByComparingTo("100");
        assertThat(resp.getPriceHigh()).isEqualByComparingTo("200");
        assertThat(resp.getBasis()).contains("人像精修");
        assertThat(resp.getScript()).contains("建议报价");
    }

    @Test
    @DisplayName("complete: content 被 ```json 围栏包裹时应去除围栏并正确解析")
    void complete_fencedJson_stripsFence() {
        String json = "{\"priceLow\":300,\"priceHigh\":500,\"basis\":\"商业拍摄\",\"script\":\"商业报价话术\"}";
        String fenced = "```json\n" + json + "\n```";
        when(stubChain().body(String.class)).thenReturn(wrapContent(fenced));

        QuoteResponse resp = client.complete("商业拍摄报价");

        assertThat(resp.getPriceLow()).isEqualByComparingTo("300");
        assertThat(resp.getPriceHigh()).isEqualByComparingTo("500");
        assertThat(resp.getBasis()).isEqualTo("商业拍摄");
        assertThat(resp.getScript()).isEqualTo("商业报价话术");
    }

    @Test
    @DisplayName("complete: 返回非法 JSON 应抛出 IllegalStateException 且含『响应解析失败』")
    void complete_invalidJson_throws() {
        when(stubChain().body(String.class)).thenReturn("not json");

        assertThatThrownBy(() -> client.complete("任意 prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("响应解析失败");
    }

    @Test
    @DisplayName("complete: RestClient 抛 RestClientResponseException(500) 应抛出含『上游服务异常（HTTP 500）』")
    void complete_upstreamError_throws() {
        RestClientResponseException ex = mockHttpError(HttpStatus.INTERNAL_SERVER_ERROR);
        when(stubChain().body(String.class)).thenThrow(ex);

        assertThatThrownBy(() -> client.complete("任意 prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上游服务异常（HTTP 500）");
    }

    // -----------------------------------------------------------------------------------------
    // chat()：apiKey 守卫
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("chat: apiKey 为 null 应抛出 IllegalStateException 且含『降级为规则模板』")
    void chat_apiKeyNull_throws() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        assertThatThrownBy(() -> client.chat("sys", "usr"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("降级为规则模板");
    }

    @Test
    @DisplayName("chat: apiKey 为空白串应抛出 IllegalStateException 且含『降级为规则模板』")
    void chat_apiKeyBlank_throws() {
        ReflectionTestUtils.setField(client, "apiKey", "   ");

        assertThatThrownBy(() -> client.chat("sys", "usr"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("降级为规则模板");
    }

    // -----------------------------------------------------------------------------------------
    // chat()：正常解析路径
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("chat: 返回纯文本 content 应去除首尾空白后返回")
    void chat_plainText_returnsTrimmed() {
        when(stubChain().body(String.class)).thenReturn(wrapContent("  你好，摄影师   "));

        String result = client.chat("你是助手", "你好");

        assertThat(result).isEqualTo("你好，摄影师");
    }

    @Test
    @DisplayName("chat: content 被 ```text 围栏包裹时应去除围栏返回正文")
    void chat_fencedText_stripsFence() {
        when(stubChain().body(String.class)).thenReturn(wrapContent("```text\n你好世界\n```"));

        String result = client.chat("你是助手", "你好");

        assertThat(result).isEqualTo("你好世界");
    }

    @Test
    @DisplayName("chat: 返回非法 JSON 应抛出 IllegalStateException 且含『响应解析失败』")
    void chat_invalidJson_throws() {
        when(stubChain().body(String.class)).thenReturn("not json");

        assertThatThrownBy(() -> client.chat("sys", "usr"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("响应解析失败");
    }

    @Test
    @DisplayName("chat: RestClient 抛 RestClientResponseException(502) 应抛出含『上游服务异常』")
    void chat_upstreamError_throws() {
        RestClientResponseException ex = mockHttpError(HttpStatus.BAD_GATEWAY);
        when(stubChain().body(String.class)).thenThrow(ex);

        assertThatThrownBy(() -> client.chat("sys", "usr"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上游服务异常（HTTP 502）");
    }

    // -----------------------------------------------------------------------------------------
    // guardPromptLength()：私有方法，反射调用覆盖边界分支
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("guardPromptLength: 总长度未超上限时原样返回 user（短串）")
    void guard_withinLimit_returnsUser() throws Exception {
        Object result = invokeGuard("s", "u");
        assertThat(result).isEqualTo("u");
    }

    @Test
    @DisplayName("guardPromptLength: system/user 均为 null 且未超上限时返回 null")
    void guard_nullSystemAndNullUser_returnsNull() throws Exception {
        Object result = invokeGuard(null, null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("guardPromptLength: system 为 null、user 短串未超上限时返回原 user")
    void guard_nullSystemShortUser_returnsUser() throws Exception {
        Object result = invokeGuard(null, "短");
        assertThat(result).isEqualTo("短");
    }

    @Test
    @DisplayName("guardPromptLength: 超长且 user 非 null 超 budget 时截断到 budget")
    void guard_exceeds_truncatesUser() throws Exception {
        String user = "x".repeat(9000);
        Object result = invokeGuard("s", user);
        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).hasSize(7999);
    }

    @Test
    @DisplayName("guardPromptLength: system 为 null 且 user 超长时按 8000 budget 截断")
    void guard_nullSystemLongUser_truncates() throws Exception {
        String user = "x".repeat(9000);
        Object result = invokeGuard(null, user);
        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).hasSize(8000);
    }

    @Test
    @DisplayName("guardPromptLength: 超长且 user 为 null 时返回 null（user==null 短路分支）")
    void guard_exceeds_userNull_returnsNull() throws Exception {
        // system 超过上限、user 为 null，使 total>MAX 且命中 user==null 短路返回 null
        String system = "x".repeat(9000);
        Object result = invokeGuard(system, null);
        assertThat(result).isNull();
    }
}
