import { defineConfig } from "vitest/config";
import path from "node:path";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": path.resolve(__dirname, "./src") },
  },
  test: {
    environment: "jsdom",
    globals: true,
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov", "json-summary"],
      include: [
        "src/lib/format.ts",
        "src/lib/status.ts",
        "src/lib/utils.ts",
        "src/lib/auth.ts",
      ],
    },
  },
});
