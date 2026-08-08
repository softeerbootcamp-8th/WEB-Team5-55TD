import { describe, expect, it, vi } from "vitest";

const getAuctionDetail = vi.fn();
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  notFound: () => new Error("not found"),
}));
vi.mock("@/api/auctions", () => ({ getAuctionDetail }));

describe("실시간 경매 loader", () => {
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
});
