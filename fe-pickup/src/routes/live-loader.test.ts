import { beforeEach, describe, expect, it, vi } from "vitest";
import { setAuthenticated } from "@/lib/auth";

const getAuctionDetail = vi.fn();
const refreshAccessToken = vi.fn();
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  notFound: () => new Error("not found"),
}));
vi.mock("@/api/auctions", () => ({ getAuctionDetail }));
vi.mock("@/api/mutator/custom-instance", () => ({ refreshAccessToken }));

describe("실시간 경매 loader", () => {
  beforeEach(() => {
    localStorage.clear();
    getAuctionDetail.mockReset();
    refreshAccessToken.mockReset();
  });

  it("경매 상세를 loader 결과로 반환한다", async () => {
    getAuctionDetail.mockResolvedValue({ id: "1", cardName: "Mewtwo" });
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const loader = Route.options.loader as (context: {
      params: { auctionId: string };
    }) => Promise<unknown>;
    await expect(loader({ params: { auctionId: "1" } })).resolves.toEqual({
      auction: { id: "1", cardName: "Mewtwo" },
    });
  });

  it("로그인 상태면 진입 시 access-token을 선제로 갱신한다", async () => {
    setAuthenticated(true);
    refreshAccessToken.mockResolvedValue(undefined);
    getAuctionDetail.mockResolvedValue({ id: "1", cardName: "Mewtwo" });
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const loader = Route.options.loader as (context: {
      params: { auctionId: string };
    }) => Promise<unknown>;
    await loader({ params: { auctionId: "1" } });
    expect(refreshAccessToken).toHaveBeenCalledTimes(1);
  });

  it("비로그인 상태면 access-token 갱신을 호출하지 않는다", async () => {
    setAuthenticated(false);
    getAuctionDetail.mockResolvedValue({ id: "1", cardName: "Mewtwo" });
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const loader = Route.options.loader as (context: {
      params: { auctionId: string };
    }) => Promise<unknown>;
    await loader({ params: { auctionId: "1" } });
    expect(refreshAccessToken).not.toHaveBeenCalled();
  });

  it("access-token 선제 갱신이 실패해도 loader 자체는 실패하지 않는다", async () => {
    setAuthenticated(true);
    refreshAccessToken.mockRejectedValue(new Error("network error"));
    getAuctionDetail.mockResolvedValue({ id: "1", cardName: "Mewtwo" });
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const loader = Route.options.loader as (context: {
      params: { auctionId: string };
    }) => Promise<unknown>;
    await expect(loader({ params: { auctionId: "1" } })).resolves.toEqual({
      auction: { id: "1", cardName: "Mewtwo" },
    });
  });
});
