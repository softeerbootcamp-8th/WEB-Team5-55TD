import {
  MY_BID_STATUS_META,
  PRODUCT_STATUS_META,
  ProductStatus,
} from "@/lib/status";
import { describe, expect, it } from "vitest";

describe("status metadata", () => {
  it("defines a badge for every product status", () => {
    expect(Object.keys(PRODUCT_STATUS_META)).toHaveLength(5);
    expect(PRODUCT_STATUS_META[ProductStatus.AUCTION_LIVE]).toEqual({
      label: "경매 진행 중",
      variant: "live",
    });
  });

  it("defines labels and variants for bid statuses", () => {
    expect(MY_BID_STATUS_META.OUTBID.label).toBe("추월됨");
    expect(MY_BID_STATUS_META.WON.variant).toBe("success");
    expect(MY_BID_STATUS_META.LOST.variant).toBe("neutral");
  });
});
