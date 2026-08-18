package com.photogai.config;

import com.photogai.config.WechatConfig.WechatApp;
import com.photogai.modules.auth.enums.WechatAppType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信装配配置测试（纯 new + ReflectionTestUtils，不连 PG、不启 Spring 上下文）。
 *
 * <p>覆盖 {@code WechatConfig} 的 appOf 三端 switch 分支、nullToEmpty 的两分支，
 * 以及内部 record {@code WechatApp#configured()} 的 4 个判空/判空串分支 + equals/hashCode/toString。
 * 顺带断言 wechatRestClient() 非 null、三个 URL getter 默认值与 isAutoRegister() 默认 true。
 */
class WechatConfigTest {

    @Test
    void appOf_switch_nullToEmpty_and_configured() {
        WechatConfig cfg = new WechatConfig();

        // 默认字段为 "" -> 三端 appOf 均返回 WechatApp("", "")，configured()=false
        WechatApp mp = cfg.appOf(WechatAppType.MP);
        WechatApp app = cfg.appOf(WechatAppType.APP);
        WechatApp web = cfg.appOf(WechatAppType.WEB);
        assertFalse(mp.configured());
        assertFalse(app.configured());
        assertFalse(web.configured());
        // 覆盖 switch 三个 case 分支
        assertEquals("", mp.appid());
        assertEquals("", app.appid());
        assertEquals("", web.appid());

        // nullToEmpty 分支1：字段置 null -> 返回 ""
        ReflectionTestUtils.setField(cfg, "mpAppid", null);
        assertEquals("", cfg.appOf(WechatAppType.MP).appid());

        // nullToEmpty 分支2：字段置 "  x  " -> 返回 trim 后的 "x"
        ReflectionTestUtils.setField(cfg, "mpAppid", "  x  ");
        assertEquals("x", cfg.appOf(WechatAppType.MP).appid());

        // WechatApp.configured() 四个判断全部分支（appid/secret 的 !=null 与 !isBlank()）
        assertFalse(new WechatConfig.WechatApp(null, "y").configured()); // appid == null
        assertFalse(new WechatConfig.WechatApp("", "y").configured());   // appid isBlank
        assertFalse(new WechatConfig.WechatApp("x", null).configured()); // secret == null
        assertFalse(new WechatConfig.WechatApp("x", "").configured());   // secret isBlank
        assertTrue(new WechatConfig.WechatApp("x", "y").configured());   // 两者齐全

        // WechatApp.equals / hashCode / toString 分支
        WechatApp a1 = new WechatConfig.WechatApp("x", "y");
        WechatApp a2 = new WechatConfig.WechatApp("x", "y");
        assertTrue(a1.equals(a1));                       // 同引用
        assertFalse(a1.equals(null));                    // o == null
        assertFalse(a1.equals("not-app"));               // instanceof 为 false
        assertTrue(a1.equals(a2));                       // 两个组件均相等
        assertFalse(a1.equals(new WechatConfig.WechatApp("x", "z"))); // secret 不等
        assertFalse(a1.equals(new WechatConfig.WechatApp("z", "y"))); // appid 不等
        assertFalse(a1.equals(new WechatConfig.WechatApp(null, "y"))); // appid null vs "x"
        assertFalse(a1.equals(new WechatConfig.WechatApp("x", null))); // secret null vs "y"
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotNull(a1.toString());

        // Bean / getter 默认值
        assertNotNull(cfg.wechatRestClient());
        assertTrue(cfg.wechatRestClient() instanceof RestClient);
        assertEquals("https://api.weixin.qq.com/sns/jscode2session", cfg.getCode2SessionUrl());
        assertEquals("https://api.weixin.qq.com/sns/oauth2/access_token", cfg.getOauth2AccessTokenUrl());
        assertEquals("https://api.weixin.qq.com/sns/userinfo", cfg.getUserinfoUrl());
        assertTrue(cfg.isAutoRegister());
    }
}
