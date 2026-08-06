/**
 * 故障注入：taro 立即以非 0 退出且不产出任何产物。
 * 配合"运行前预置陈旧 dist"（在 harness 中 setup），验证先 rm -rf dist
 * 能杜绝陈旧产物被误判成功（缺陷 C 的另一面）。
 */
setTimeout(() => process.exit(1), 500);
