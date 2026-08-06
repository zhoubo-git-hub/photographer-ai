import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@photogai/shared/http';

/** 不值得重试的业务错误码：重试只会重复触发弹窗/浪费配额。 */
const NO_RETRY_CODES: readonly number[] = [400, 401, 402, 403, 404, 409];

/**
 * 小程序端 QueryClient。
 * 相比 Web 更保守：小程序无窗口焦点概念、网络更弱，故关闭聚焦重取、重试次数收敛到 1。
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount: number, error: unknown): boolean => {
        if (error instanceof ApiError && NO_RETRY_CODES.includes(error.code)) {
          return false;
        }
        return failureCount < 1;
      },
      refetchOnWindowFocus: false,
      refetchOnReconnect: true,
      staleTime: 30 * 1000,
      gcTime: 5 * 60 * 1000,
    },
    mutations: {
      retry: 0,
    },
  },
});

export default queryClient;
