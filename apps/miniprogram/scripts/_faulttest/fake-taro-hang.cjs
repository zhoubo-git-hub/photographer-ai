/**
 * 故障注入：taro 永远不产出产物、永远不退出。
 * 配合 BUILD_TIMEOUT_MS 调小，验证超时兜底会失败退出（缺陷 B）。
 */
setInterval(() => {}, 1000);
