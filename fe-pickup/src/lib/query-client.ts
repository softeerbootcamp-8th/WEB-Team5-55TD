import { QueryClient } from "@tanstack/react-query";

/** 앱 전역 TanStack Query 클라이언트 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000, // 30s — 실시간 화면은 개별 쿼리에서 override
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
