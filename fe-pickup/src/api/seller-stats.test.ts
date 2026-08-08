import { describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/api/mutator/custom-instance", () => ({ axiosInstance: { get } }));

describe("seller stats api", () => {
  it("maps backend statistic names to UI names", async () => {
    get.mockResolvedValue({
      data: {
        registeredConsignments: 1,
        scheduledAuctions: 2,
        ongoingAuctions: 3,
        wonConsignments: 4,
      },
    });
    const { getMySellerStats } = await import("@/api/seller-stats");
    await expect(getMySellerStats()).resolves.toEqual({
      registered: 1,
      scheduled: 2,
      ongoing: 3,
      sold: 4,
    });
  });
});
