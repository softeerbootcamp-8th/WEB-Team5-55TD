import { describe, expect, it } from "vitest";

describe("query client", () => {
  it("전역 QueryClient를 제공한다", async () => {
    const { queryClient } = await import("@/lib/query-client");
    expect(queryClient).toBeDefined();
    expect(queryClient.getDefaultOptions().queries?.retry).toBe(1);
  });
});
