import { describe, expect, it } from "vitest";
import { cardThumbnailUrl } from "@/lib/card-image";

describe("카드 썸네일 URL", () => {
  it("TCGdex 고해상도 이미지는 저해상도로 낮춘다", () => {
    expect(
      cardThumbnailUrl("https://assets.tcgdex.net/en/swsh/swsh3/136/high.webp"),
    ).toBe("https://assets.tcgdex.net/en/swsh/swsh3/136/low.webp");
  });

  it("TCGdex 이외의 이미지 URL은 그대로 둔다", () => {
    expect(cardThumbnailUrl("https://images.pickup/media/a.jpg")).toBe(
      "https://images.pickup/media/a.jpg",
    );
    expect(cardThumbnailUrl("https://cdn.example/card/high.webp")).toBe(
      "https://cdn.example/card/high.webp",
    );
  });

  it("값이 없으면 그대로 돌려준다", () => {
    expect(cardThumbnailUrl(undefined)).toBeUndefined();
    expect(cardThumbnailUrl("")).toBe("");
  });
});
