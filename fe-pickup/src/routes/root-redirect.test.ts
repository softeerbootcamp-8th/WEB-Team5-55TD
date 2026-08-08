import { describe, expect, it, vi } from "vitest";

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => options,
  redirect: (value: unknown) => value,
}));

describe("루트 리다이렉트", () => {
  it("홈으로 이동한다", async () => {
    const { Route } = await import("@/routes/index");
    const beforeLoad = (Route as unknown as { beforeLoad: () => never })
      .beforeLoad;
    expect(() => beforeLoad()).toThrow();
  });
});
