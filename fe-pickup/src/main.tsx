import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider, createRouter } from "@tanstack/react-router";
import { QueryClientProvider } from "@tanstack/react-query";

import { routeTree } from "./routeTree.gen";
import { queryClient } from "./lib/query-client";
import { NotFoundPage } from "./components/domain/not-found-page";
import "./styles/globals.css";

// 라우터 인스턴스 생성 (queryClient 를 라우트 컨텍스트로 주입)
const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: "intent",
  scrollRestoration: true,
  defaultNotFoundComponent: NotFoundPage,
});

// 타입 안전성 — 라우터 인스턴스 타입 등록
declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}

const rootElement = document.getElementById("root")!;

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
