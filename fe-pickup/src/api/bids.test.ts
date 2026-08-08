import { AxiosError } from "axios";
import { describe, expect, it, vi } from "vitest";

const { post, get } = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }));
vi.mock("@/api/mutator/custom-instance", () => ({
  axiosInstance: { post, get },
}));

describe("bids api", () => {
  it("returns server bid error and fallback messages", async () => {
    const { getBidErrorMessage, placeBid } = await import("@/api/bids");
    const error = new AxiosError("bad");
    error.response = { data: { message: "잔액 부족" } } as never;
    expect(getBidErrorMessage(error)).toBe("잔액 부족");
    expect(getBidErrorMessage(new Error("bad"))).toContain("입찰에 실패");
    post.mockResolvedValue({ data: { bidId: 1, bidPrice: 1000 } });
    await expect(placeBid("1", 1000)).resolves.toEqual({
      bidId: 1,
      bidPrice: 1000,
    });
    expect(post).toHaveBeenCalledWith("/auctions/1/bids", { bidPrice: 1000 });
  });
});
