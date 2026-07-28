import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import path from "node:path";

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");

  return {
    plugins: [
      // TanStack Router plugin must come BEFORE the react plugin.
      tanstackRouter({
        target: "react",
        routesDirectory: "./src/routes",
        generatedRouteTree: "./src/routeTree.gen.ts",
        autoCodeSplitting: true,
      }),
      react(),
      tailwindcss(),
    ],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    server: {
      host: true, // 0.0.0.0 바인딩 — LAN(사설 IP)에서 접근 허용
      port: 5173,
      proxy: {
        // 백엔드 API 프록시 — 백엔드는 "/api" 접두사 없이 라우팅하므로(예: GET /healthcheck) rewrite 로 제거한다.
        "/api": {
          target: env.VITE_PROXY_TARGET ?? "http://localhost:8080",
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ""),
        },
      },
    },
  };
});
