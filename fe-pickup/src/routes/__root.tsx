import { createRootRouteWithContext, Outlet } from "@tanstack/react-router";
import type { QueryClient } from "@tanstack/react-query";
import { lazy, Suspense } from "react";
import { Toaster } from "sonner";

/** 라우트 컨텍스트 — main.tsx 에서 queryClient 주입 */
export interface RouterContext {
  queryClient: QueryClient;
}

// 개발 전용 devtools (프로덕션 번들에서 제외)
const TanStackRouterDevtools = import.meta.env.PROD
  ? () => null
  : lazy(() =>
      import("@tanstack/router-devtools").then((m) => ({
        default: m.TanStackRouterDevtools,
      })),
    );

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
});

function RootLayout() {
  return (
    <div className="min-h-dvh bg-background text-foreground">
      <Outlet />
      <Toaster position="top-center" richColors />
      <Suspense>
        <TanStackRouterDevtools position="bottom-right" />
      </Suspense>
    </div>
  );
}
