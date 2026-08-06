import { Button, Text, View } from '@tarojs/components';
import { ApiError } from '@photogai/shared/http';
import { messageOfErrorCode } from '@photogai/shared/domain';
import './index.scss';

interface StateViewProps {
  /** 是否加载中。 */
  loading?: boolean;
  /** 错误对象（来自 react-query 的 error 或 catch 到的异常）。 */
  error?: unknown;
  /** 是否为空数据。 */
  empty?: boolean;
  /** 空态文案。 */
  emptyText?: string;
  /** 重试回调，传入时错误态显示「重试」按钮。 */
  onRetry?: () => void;
}

/** 从任意异常里提取用户可读文案（优先后端 message，回退错误码兜底）。 */
function resolveErrorText(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message || messageOfErrorCode(error.code);
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return '加载失败，请稍后重试';
}

/**
 * 加载 / 错误 / 空 三态统一渲染。
 * 三态都不命中时返回 null，由调用方渲染正常内容。
 *
 * 用法：
 * ```tsx
 * {isLoading || error || list.length === 0 ? (
 *   <StateView loading={isLoading} error={error} empty={list.length === 0} onRetry={refetch} />
 * ) : (
 *   list.map((item) => <Card key={item.id} data={item} />)
 * )}
 * ```
 */
export default function StateView({
  loading = false,
  error = null,
  empty = false,
  emptyText = '暂无数据',
  onRetry,
}: StateViewProps) {
  if (loading) {
    return (
      <View className="state-view">
        <View className="state-view__spinner" />
        <Text className="state-view__text">加载中…</Text>
      </View>
    );
  }

  if (error) {
    return (
      <View className="state-view">
        <Text className="state-view__title state-view__title--error">加载失败</Text>
        <Text className="state-view__text">{resolveErrorText(error)}</Text>
        {onRetry ? (
          <Button className="state-view__retry" onClick={onRetry}>
            重试
          </Button>
        ) : null}
      </View>
    );
  }

  if (empty) {
    return (
      <View className="state-view">
        <Text className="state-view__text">{emptyText}</Text>
      </View>
    );
  }

  return null;
}
