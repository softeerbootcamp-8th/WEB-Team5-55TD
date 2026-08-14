import { describe, expect, it, vi, beforeEach } from "vitest";

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => options,
  redirect: (value: unknown) => value,
  Link: () => null,
  Outlet: () => null,
}));

vi.mock("@/lib/auth", () => ({
  isAuthenticated: vi.fn(),
}));

vi.mock("@/components/layout/gnb", () => ({ Gnb: () => null }));

type GuardedRoute = { beforeLoad: () => void };

async function beforeLoadOf(modulePath: string) {
  const mod = await import(/* @vite-ignore */ modulePath);
  return (mod.Route as unknown as GuardedRoute).beforeLoad;
}

describe("로그인이 필요한 라우트 가드", () => {
  beforeEach(async () => {
    vi.resetModules();
    const { isAuthenticated } = await import("@/lib/auth");
    vi.mocked(isAuthenticated).mockReturnValue(false);
  });

  it.each([
    ["@/routes/seller/route", "셀러 레이아웃"],
    ["@/routes/_buyer/points", "포인트 내역"],
    ["@/routes/_buyer/settings", "계정 설정"],
    ["@/routes/_buyer/bids", "입찰 내역"],
    ["@/routes/_buyer/watchlist", "관심 목록"],
  ])(
    "비로그인 상태로 %s(%s)에 접근하면 로그인 페이지로 리다이렉트한다",
    async (modulePath) => {
      const beforeLoad = await beforeLoadOf(modulePath);
      expect(() => beforeLoad()).toThrow();
    },
  );

  it.each([
    ["@/routes/seller/route", "셀러 레이아웃"],
    ["@/routes/_buyer/points", "포인트 내역"],
    ["@/routes/_buyer/settings", "계정 설정"],
    ["@/routes/_buyer/bids", "입찰 내역"],
    ["@/routes/_buyer/watchlist", "관심 목록"],
  ])("로그인 상태로 %s(%s)에 접근하면 통과한다", async (modulePath) => {
    const { isAuthenticated } = await import("@/lib/auth");
    vi.mocked(isAuthenticated).mockReturnValue(true);

    const beforeLoad = await beforeLoadOf(modulePath);

    expect(() => beforeLoad()).not.toThrow();
  });
});
