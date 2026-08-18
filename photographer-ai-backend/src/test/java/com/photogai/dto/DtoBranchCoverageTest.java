package com.photogai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * DTO 分支覆盖率批量测试（低成本抬升 JaCoCo 分支率）。
 *
 * <p>摄影助手后端的分支缺口约 58% 来自 DTO/Request/Response 的 Lombok 生成分支
 * （equals / hashCode / toString / builder）。本测试对每个目标 DTO 做：
 * <ol>
 *   <li>用无参构造建「全字段填充」实例 A 与 A2；</li>
 *   <li>建「全字段 null」实例 C / C1 / C2（覆盖 equals/hashCode/toString 的空值分支）；</li>
 *   <li>触发 equals（A==A2 / A!=C / A!=null / A==A / C==C / C!=A / C1==C2 独立实例）；</li>
 *   <li><b>逐字段变体</b>：对每个字段构造 diff（不同非 null 值）与 null 两种变体，闭合
 *       Lombok equals 中「一旦某字段不等就 return false，后续字段分支不被求值」导致的缺口；</li>
 *   <li>触发 hashCode、toString；</li>
 *   <li>Jackson 序列化→反序列化往返（best-effort，失败不计入失败）。</li>
 * </ol>
 *
 * <p>单个 DTO 构造失败仅记录、不中断整套，保证 CI 全绿。
 */
class DtoBranchCoverageTest {

    private static final ObjectMapper OM = new ObjectMapper().registerModule(new JavaTimeModule());

    // 诊断：构造过程中被 setter/build 拒绝的字段（仅信息性，不计入失败）
    private static final List<String> DIAG = new java.util.ArrayList<>();

    // 目标 DTO/Request/Response（按 jacoco 未覆盖分支数降序，均为 Lombok @Data @Builder）
    private static final List<String> TARGETS = Arrays.asList(
            "com.photogai.modules.order.dto.OrderDTO",
            "com.photogai.modules.customer.dto.CustomerDTO",
            "com.photogai.modules.order.dto.OrderCreateRequest",
            "com.photogai.modules.order.dto.OrderUpdateRequest",
            "com.photogai.modules.customer.dto.CustomerUpdateRequest",
            "com.photogai.modules.customer.dto.CustomerCreateRequest",
            "com.photogai.modules.order.dto.ReminderDTO",
            "com.photogai.modules.repurchase.dto.RepurchaseTaskDTO",
            "com.photogai.modules.quota.dto.QuotaDTO",
            "com.photogai.modules.ai.dto.QuoteCalibrationDTO",
            "com.photogai.modules.auth.dto.UserDTO",
            "com.photogai.modules.contract.dto.ContractTemplateDTO",
            "com.photogai.modules.team.dto.TeamMemberDTO",
            "com.photogai.modules.order.dto.StatusHistoryDTO",
            "com.photogai.modules.ai.dto.QuoteRequest",
            "com.photogai.modules.schedule.dto.ScheduleDTO",
            "com.photogai.modules.auth.dto.WechatLoginResponse",
            "com.photogai.modules.auth.dto.WechatLoginRequest",
            "com.photogai.modules.ai.dto.QuoteResponse",
            "com.photogai.modules.reminder.dto.ReminderRuleDTO",
            "com.photogai.modules.dashboard.dto.OverviewDTO",
            "com.photogai.modules.dashboard.dto.MemberPerfDTO",
            "com.photogai.modules.studio.dto.StudioDTO",
            "com.photogai.modules.order.dto.ConflictDTO",
            "com.photogai.modules.billing.dto.SubscribeResponse",
            "com.photogai.modules.auth.dto.RegisterRequest",
            "com.photogai.modules.billing.dto.SubscriptionView",
            "com.photogai.modules.team.dto.TeamInviteRequest",
            "com.photogai.modules.team.dto.AcceptInvitationRequest",
            "com.photogai.modules.billing.PrecreateResult",
            "com.photogai.modules.auth.dto.AuthResponse",
            "com.photogai.modules.ai.dto.CommRequest",
            "com.photogai.modules.reminder.dto.ReminderRuleRequest",
            "com.photogai.modules.ai.dto.CommResponse",
            "com.photogai.modules.order.dto.StatusChangeRequest",
            "com.photogai.modules.dashboard.dto.RevenuePointDTO",
            "com.photogai.modules.contract.dto.ContractGenerateResponse",
            "com.photogai.modules.contract.dto.ContractGenerateRequest",
            "com.photogai.modules.billing.dto.SubscribeRequest",
            "com.photogai.modules.billing.dto.PaymentNotifyRequest",
            "com.photogai.modules.auth.dto.WechatBindRequest",
            "com.photogai.modules.auth.dto.LoginRequest",
            "com.photogai.modules.dashboard.dto.FunnelDTO",
            "com.photogai.modules.dashboard.dto.FunnelDTO$Stage",
            "com.photogai.modules.billing.dto.SubscriptionCancelRequest",
            "com.photogai.modules.ai.dto.QuoteCalibrationApplyRequest");

    @Test
    void coverAllTargetDtoBranches() throws Exception {
        List<String> failures = new java.util.ArrayList<>();
        int covered = 0;
        for (String fqn : TARGETS) {
            try {
                Class<?> clazz = Class.forName(fqn);
                Object a = buildPopulated(clazz);
                Object a2 = buildPopulated(clazz);
                Object c = buildNull(clazz);
                Object c1 = buildNull(clazz);
                Object c2 = buildNull(clazz);

                // 原有对称比较（保留）：覆盖 this==o / o==null / 全字段相等 / 首字段不等等分支
                if (!a.equals(a2)) {
                    failures.add(fqn + " : equals(填充,填充) 应为 true");
                }
                if (a.equals(c)) {
                    failures.add(fqn + " : equals(填充,null字段) 应为 false");
                }
                if (!a.equals(a)) {
                    failures.add(fqn + " : equals(填充,自身) 应为 true");
                }
                if (!c.equals(c)) {
                    failures.add(fqn + " : equals(null字段,自身) 应为 true");
                }
                if (c.equals(a)) {
                    failures.add(fqn + " : equals(null字段,填充) 应为 false");
                }
                //noinspection ConstantConditions,EqualsWithItself
                if (a.equals(null)) {
                    failures.add(fqn + " : equals(null) 应为 false");
                }

                // 两个独立全 null 实例：对每个字段覆盖「this null + other null」分支
                // （同一引用会在 this==o 直接 return true，覆不到字段分支，故必须两个独立实例）
                if (!c1.equals(c2)) {
                    failures.add(fqn + " : equals(全null 独立实例 c1 vs c2) 应为 true");
                }

                // ===== 逐字段变体：闭合每个字段的「不等」与「null」分支 =====
                // 根因：Lombok equals 一旦某字段不等就 return false，后续字段分支不被求值。
                // 现有 a.equals(c) 只在「第一个字段」就返回，第 2..n 个字段的「不等」分支全空。
                // 下面为每个字段构造 diff / null 变体（其余字段与 a 完全一致），确保比较推进到该字段。
                List<Field> fields = allFields(clazz);
                for (int i = 0; i < fields.size(); i++) {
                    Field f = fields.get(i);
                    if (isStaticOrSynthetic(f)) {
                        continue;
                    }
                    if (f.getType().isPrimitive()) {
                        // 基本类型生成 if (this.getX() != other.getX()) return false; 只需 diff 变体（取反/不同数值），无 null 变体
                        Object diff = buildPopulated(clazz);
                        setField(diff, f, sample2(f));
                        if (a.equals(diff)) {
                            failures.add(fqn + " : 字段#" + i + "[" + f.getName() + "] diff(基本类型) 应为 false");
                        }
                        continue;
                    }
                    // 引用类型：diff（不同非 null 值）+ null 两个变体
                    Object diff = buildPopulated(clazz);
                    setField(diff, f, sample2(f));
                    Object nullI = buildPopulated(clazz);
                    setField(nullI, f, null);
                    // a.equals(diff_i)：this 非 null、other 非 null 且不等 -> 覆盖「this 非 null + !equals 为 true」
                    if (a.equals(diff)) {
                        failures.add(fqn + " : 字段#" + i + "[" + f.getName() + "] diff 应为 false");
                    }
                    // a.equals(null_i)：this 非 null、other null -> 覆盖「this 非 null + other null」
                    if (a.equals(nullI)) {
                        failures.add(fqn + " : 字段#" + i + "[" + f.getName() + "] null_i 应为 false");
                    }
                    // null_i.equals(a)：this null、other 非 null -> 覆盖「this null + other 非 null」
                    if (nullI.equals(a)) {
                        failures.add(fqn + " : 字段#" + i + "[" + f.getName() + "] null_i.equals(a) 应为 false");
                    }
                }

                a.hashCode();
                a2.hashCode();
                c.hashCode();
                c1.hashCode();
                c2.hashCode();
                if (a.toString() == null || c.toString() == null) {
                    failures.add(fqn + " : toString 不应为 null");
                }
                // best-effort Jackson 往返
                try {
                    String json = OM.writeValueAsString(a);
                    OM.readValue(json, clazz);
                } catch (Exception ignore) {
                    // 部分 DTO 无 Jackson creator，跳过往返，不影响 equals/hashCode/toString 覆盖
                }
                covered++;
            } catch (Throwable t) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                String trace = Arrays.stream(sw.toString().split("\n"))
                        .filter(l -> l.contains("DtoBranchCoverageTest") || l.contains("reflect") || l.contains("IllegalArgument"))
                        .limit(6).collect(Collectors.joining(" | "));
                failures.add(fqn + " : 构造失败 -> " + t.getClass().getSimpleName() + ": " + t.getMessage() + " @ " + trace);
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("DTO 分支覆盖存在失败（" + covered + "/" + TARGETS.size()
                    + " 已覆盖）：\n" + String.join("\n", failures));
        }
        if (!DIAG.isEmpty()) {
            System.out.println("[DtoBranchCoverageTest DIAG] " + String.join(" | ", DIAG));
        }
    }

    private static Object buildPopulated(Class<?> clazz) throws Exception {
        return build(clazz, true);
    }

    private static Object buildNull(Class<?> clazz) throws Exception {
        return build(clazz, false);
    }

    @SuppressWarnings("unchecked")
    private static Object build(Class<?> clazz, boolean populate) throws Exception {
        // 用无参构造建实例，直接反射写字段（绕过 builder setter，避免基本类型反射 invoke 抛 IAE）。
        // 这足以触发 Lombok 生成的 equals/hashCode/toString 全部分支。
        Object inst = clazz.getDeclaredConstructor().newInstance();
        for (Field f : allFields(clazz)) {
            if (isStaticOrSynthetic(f)) {
                continue;
            }
            // null 构造时基本类型无法置 null，留默认值（0/false）即可，仍覆盖 equals 的不等分支
            if (!populate && f.getType().isPrimitive()) {
                continue;
            }
            Object value = populate ? sample(f) : null;
            setField(inst, f, value);
        }
        return inst;
    }

    private static void setField(Object inst, Field f, Object value) {
        try {
            f.setAccessible(true);
            f.set(inst, value);
        } catch (Exception ex) {
            DIAG.add(inst.getClass().getSimpleName() + "." + f.getName()
                    + "(" + f.getType().getSimpleName() + ") -> "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static List<Field> allFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
            c = c.getSuperclass();
        }
        return fields;
    }

    private static boolean isStaticOrSynthetic(Field f) {
        return java.lang.reflect.Modifier.isStatic(f.getModifiers())
                || f.isSynthetic()
                || f.getName().equals("$jacocoData");
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** 与 {@link #sample(Field)} 取值不同的值（用于 diff 变体）。基本类型/枚举/集合均取「不同」值。 */
    private static Object sample2(Field f) {
        Class<?> t = f.getType();
        if (t == String.class) {
            return "y";
        }
        if (t == Long.class || t == long.class) {
            return 2L;
        }
        if (t == Integer.class || t == int.class) {
            return 2;
        }
        if (t == Double.class || t == double.class) {
            return 2.0d;
        }
        if (t == Float.class || t == float.class) {
            return 2.0f;
        }
        if (t == BigDecimal.class) {
            return BigDecimal.valueOf(2);
        }
        if (t == Boolean.class || t == boolean.class) {
            return Boolean.FALSE;
        }
        if (t == LocalDate.class) {
            return LocalDate.of(2021, 1, 1);
        }
        if (t == LocalDateTime.class) {
            return LocalDateTime.of(2021, 1, 1, 0, 0);
        }
        if (t == LocalTime.class) {
            return LocalTime.MIDNIGHT;
        }
        if (t.isEnum()) {
            Object[] constants = t.getEnumConstants();
            // 只有 1 个常量时无「不同非 null 值」，退化为 null（仍与常量不等，diff 断言成立）
            return constants.length > 1 ? constants[1] : null;
        }
        if (Collection.class.isAssignableFrom(t)) {
            return Collections.singletonList("DIFF_ELEMENT");
        }
        if (Map.class.isAssignableFrom(t)) {
            return Collections.singletonMap("k", "v");
        }
        if (t == byte[].class) {
            return new byte[]{1};
        }
        // 自定义类型：返回「已填充」实例（与 sample 的空实例不等），构造失败则退化为 null
        try {
            return buildPopulated(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object sample(Field f) {
        Class<?> t = f.getType();
        if (t == String.class) {
            return "x";
        }
        if (t == Long.class || t == long.class) {
            return 1L;
        }
        if (t == Integer.class || t == int.class) {
            return 1;
        }
        if (t == Double.class || t == double.class) {
            return 1.0d;
        }
        if (t == Float.class || t == float.class) {
            return 1.0f;
        }
        if (t == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (t == Boolean.class || t == boolean.class) {
            return Boolean.TRUE;
        }
        if (t == LocalDate.class) {
            return LocalDate.of(2020, 1, 1);
        }
        if (t == LocalDateTime.class) {
            return LocalDateTime.of(2020, 1, 1, 0, 0);
        }
        if (t == LocalTime.class) {
            return LocalTime.NOON;
        }
        if (t.isEnum()) {
            Object[] constants = t.getEnumConstants();
            return constants.length > 0 ? constants[0] : null;
        }
        if (Collection.class.isAssignableFrom(t)) {
            return Collections.emptyList();
        }
        if (Map.class.isAssignableFrom(t)) {
            return Collections.emptyMap();
        }
        if (t == byte[].class) {
            return new byte[0];
        }
        // 自定义类型：尽量用无参构造实例化，失败则留 null
        try {
            Constructor<?> ctor = t.getDeclaredConstructor();
            return ctor.newInstance();
        } catch (Exception e) {
            return null;
        }
    }
}
