#!/usr/bin/env bash
# 摄影师AI — 后端动态冒烟测试（模拟动态测试）
# -------------------------------------------------------------
# 用法:
#   DEEPSEEK_API_KEY=sk-xxxx bash smoke-test.sh
# 说明:
#   - 不写死任何 API key；key 仅从环境变量 DEEPSEEK_API_KEY 读取。
#   - 每次运行使用带时间戳的唯一用户名，可安全重复跑多轮（测试三遍）。
#   - 覆盖三处修复相关链路: 注册邮箱校验(400) / 邮箱查重(400) /
#     AI 报价(200, 走 DeepSeek 或优雅降级) / 订单创建 / 订单列表。
# -------------------------------------------------------------
set -u
BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api"
TS=$(date +%s)
PASS=0; FAIL=0
BODY=/tmp/photogai_resp.body

green() { echo -e "\033[32m[PASS]\033[0m $1"; PASS=$((PASS+1)); }
red()   { echo -e "\033[31m[FAIL]\033[0m $1"; FAIL=$((FAIL+1)); }
info()  { echo -e "\033[36m[INFO]\033[0m $1"; }

echo "=================================================="
echo " 摄影师AI 后端动态冒烟测试"
echo " 目标: $BASE_URL"
echo " DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:+SET}${DEEPSEEK_API_KEY:-UNSET}"
echo " 轮次标识: $TS"
echo "=================================================="

# 发请求: method path [body] [token] -> 设置 HTTP_STATUS 与写入 $BODY
req() {
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local -a args=(-s -o "$BODY" -w "%{http_code}" -X "$method" "$API$path")
  if [ -n "$token" ]; then args+=(-H "Authorization: Bearer $token"); fi
  if [ -n "$body" ]; then args+=(-H "Content-Type: application/json; charset=UTF-8" -d "$body"); fi
  HTTP_STATUS=$(curl "${args[@]}")
}
resp_body() { cat "$BODY"; }
# 从 Result{code,data,...} 取 data 里的 token
tok() { resp_body | grep -o '"token":"[^"]*"' | head -1 | sed 's/.*:"//; s/"//'; }

# ---------- 0. 健康检查(宽松，直连 BASE_URL，actuator 不在 /api 前缀下) ----------
HTTP_STATUS=$(curl -s -o "$BODY" -w "%{http_code}" "$BASE_URL/actuator/health")
info "health -> HTTP $HTTP_STATUS"

# ---------- 1. 注册: 邮箱格式非法 -> 期望 400 ----------
BAD_EMAIL="not-an-email"
req POST "/auth/register" "{\"username\":\"u_${TS}_a\",\"password\":\"Test@1234\",\"email\":\"$BAD_EMAIL\",\"studioName\":\"Studio_A\"}"
if [ "$HTTP_STATUS" = "400" ]; then green "邮箱格式非法被拦截 (400)"; else red "邮箱格式非法未拦截 (实际 $HTTP_STATUS)"; fi

# ---------- 2. 注册: 合法 -> 期望 200, 取 token ----------
EMAIL="user_${TS}@example.com"
req POST "/auth/register" "{\"username\":\"u_${TS}_b\",\"password\":\"Test@1234\",\"email\":\"$EMAIL\",\"studioName\":\"Studio_B\"}"
if [ "$HTTP_STATUS" = "200" ]; then
  green "合法注册成功 (200)"
  TOKEN=$(tok)
  [ -n "$TOKEN" ] && green "拿到 JWT token" || red "响应中未解析到 token"
else
  red "合法注册失败 (实际 $HTTP_STATUS): $(resp_body)"
fi

# ---------- 3. 注册: 邮箱重复 -> 期望 400 ----------
req POST "/auth/register" "{\"username\":\"u_${TS}_c\",\"password\":\"Test@1234\",\"email\":\"$EMAIL\",\"studioName\":\"Studio_C\"}"
if [ "$HTTP_STATUS" = "400" ]; then green "邮箱重复被拦截 (400)"; else red "邮箱重复未拦截 (实际 $HTTP_STATUS)"; fi

# ---------- 4. 登录 -> 期望 200, 刷新 token ----------
req POST "/auth/login" "{\"username\":\"u_${TS}_b\",\"password\":\"Test@1234\"}"
if [ "$HTTP_STATUS" = "200" ]; then
  green "登录成功 (200)"
  TOKEN=$(tok)
else
  red "登录失败 (实际 $HTTP_STATUS): $(resp_body)"
fi

# ---------- 5. AI 报价 -> 期望 200 + priceLow ----------
if [ -z "${TOKEN:-}" ]; then
  red "无 token, 跳过 AI 报价测试"
else
  req POST "/ai/quote" "{\"shootType\":\"wedding_portrait\",\"durationHours\":4,\"photoCount\":80,\"region\":\"shanghai\",\"style\":\"luxury\"}" "$TOKEN"
  if [ "$HTTP_STATUS" = "200" ]; then
    if resp_body | grep -q '"priceLow"'; then green "AI 报价返回价格区间 (200, priceLow 存在)"; else red "AI 报价 200 但无 priceLow: $(resp_body)"; fi
  elif [ "$HTTP_STATUS" = "403" ]; then
    info "AI 报价 403(免费额度/未配置 key 的优雅降级) — key=${DEEPSEEK_API_KEY:+SET}${DEEPSEEK_API_KEY:-UNSET}"
  else
    red "AI 报价异常 (实际 $HTTP_STATUS): $(resp_body)"
  fi
fi

# ---------- 5.5 建档客户 -> 期望 200 + 取 CUSTOMER_ID ----------
if [ -z "${TOKEN:-}" ]; then
  red "无 token, 跳过客户建档"
else
  req POST "/customers" "{\"name\":\"MsWang_Smoke\"}" "$TOKEN"
  if [ "$HTTP_STATUS" = "200" ]; then
    CUSTOMER_ID=$(resp_body | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')
    [ -n "$CUSTOMER_ID" ] && green "客户建档成功 (id=$CUSTOMER_ID)" || red "客户建档失败 (实际 $HTTP_STATUS): $(resp_body)"
  else
    red "客户建档失败 (实际 $HTTP_STATUS): $(resp_body)"
  fi
fi

# ---------- 6. 创建订单 -> 期望 200 + id ----------
if [ -z "${TOKEN:-}" ]; then
  red "无 token, 跳过订单创建测试"
else
  req POST "/orders" "{\"customerId\":$CUSTOMER_ID,\"title\":\"MsWang_WeddingPortrait\",\"shootType\":\"wedding_portrait\",\"status\":\"CONSULT\",\"amount\":2999.00,\"depositAmount\":1000.00,\"shootDate\":\"2026-08-10\",\"durationHours\":4,\"photoCount\":80,\"region\":\"shanghai\",\"style\":\"luxury\"}" "$TOKEN"
  if [ "$HTTP_STATUS" = "200" ]; then
    green "订单创建成功 (200)"
    ORDER_ID=$(resp_body | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')
    [ -n "$ORDER_ID" ] && green "订单 id=$ORDER_ID"
  else
    red "订单创建失败 (实际 $HTTP_STATUS): $(resp_body)"
  fi
fi

# ---------- 7. 订单列表 -> 期望 200 ----------
if [ -n "${TOKEN:-}" ]; then
  req GET "/orders" "" "$TOKEN"
  if [ "$HTTP_STATUS" = "200" ]; then green "订单列表拉取成功 (200)"; else red "订单列表失败 (实际 $HTTP_STATUS)"; fi
fi

echo "=================================================="
echo -e " 结果: \033[32m通过 $PASS\033[0m / \033[31m失败 $FAIL\033[0m"
echo "=================================================="
[ "$FAIL" = "0" ]
