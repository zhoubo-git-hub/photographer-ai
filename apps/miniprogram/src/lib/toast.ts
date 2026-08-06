import Taro from '@tarojs/taro';
import { ApiError } from '@photogai/shared/http';
import { useUiStore } from '@photogai/shared/store';
import { messageOfErrorCode } from '@photogai/shared/domain';

/**
 * uiStore → Taro 原生反馈的桥接。
 * shared 只负责把"要提示什么"写进 uiStore，"怎么提示"由各端实现。
 */

let unbind: (() => void) | null = null;

/**
 * 订阅 uiStore：
 * - toast        → Taro.showToast（消费后立即 clear，避免同文案二次触发不生效）
 * - upgradeOpen  → Taro.showModal（403 专业版门禁，引导去 Web 端升级）
 *
 * 返回取消订阅函数；重复调用会先解绑旧订阅，保证全局只有一份。
 */
export function bindUiStore(): () => void {
  if (unbind) {
    unbind();
    unbind = null;
  }

  unbind = useUiStore.subscribe((state, prev) => {
    // toast：新出现的提示才展示
    if (state.toast && state.toast !== prev.toast) {
      const { message, severity } = state.toast;
      void Taro.showToast({
        title: message,
        icon: severity === 'success' ? 'success' : 'none',
        duration: 2000,
      });
      // 立即清空，使下一次相同文案仍能触发（zustand 是引用比较）。
      useUiStore.getState().clearToast();
    }

    // 升级弹窗：由 shared 拦截器在 403/402 时 openUpgrade 触发
    if (state.upgradeOpen && !prev.upgradeOpen) {
      const content = state.upgradeMessage || '该功能为专业版专属，请前往 Web 端升级。';
      void Taro.showModal({
        title: '需要升级',
        content,
        showCancel: false,
        confirmText: '我知道了',
      }).then(() => {
        useUiStore.getState().closeUpgrade();
      });
    }
  });

  return unbind;
}

/**
 * 统一的错误提示：优先用后端 message，缺失时回退 shared 的错误码兜底文案。
 * 页面 catch 到任何异常都可直接丢给它，无需各自判断类型。
 */
export function showApiError(err: unknown, fallback = '操作失败，请稍后重试'): void {
  let message = fallback;
  if (err instanceof ApiError) {
    message = err.message || messageOfErrorCode(err.code);
  } else if (err instanceof Error && err.message) {
    message = err.message;
  }
  void Taro.showToast({ title: message, icon: 'none', duration: 2500 });
}

/** 成功提示。 */
export function showSuccess(message: string): void {
  void Taro.showToast({ title: message, icon: 'success', duration: 1500 });
}

/** 普通信息提示。 */
export function showInfo(message: string): void {
  void Taro.showToast({ title: message, icon: 'none', duration: 2000 });
}
